package com.envisionad.webservice.payment.businesslogiclayer;

import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscription;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionRepository;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionStatus;
import com.envisionad.webservice.payment.dataaccesslayer.StripeAccount;
import com.envisionad.webservice.payment.dataaccesslayer.StripeAccountRepository;
import com.envisionad.webservice.payment.exceptions.BundleSubscriptionNotLinkedException;
import com.stripe.model.Account;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Invoice;
import com.stripe.model.InvoiceLineItem;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class StripeWebhookService {

    /**
     * Stripe Checkout mode marking a bundle subscription. Retained after M6 removed the one-time
     * payment flow, because the account still holds pre-cutover {@code mode=payment} events that
     * must be ignored rather than mishandled if replayed.
     */
    private static final String SUBSCRIPTION_MODE = "subscription";

    private final StripeAccountRepository stripeAccountRepository;
    private final BundleSubscriptionRepository bundleSubscriptionRepository;
    private final BundlePayoutService bundlePayoutService;
    private final BundleSubscriptionService bundleSubscriptionService;

    public StripeWebhookService(StripeAccountRepository stripeAccountRepository,
                                BundleSubscriptionRepository bundleSubscriptionRepository,
                                BundlePayoutService bundlePayoutService,
                                BundleSubscriptionService bundleSubscriptionService) {
        this.stripeAccountRepository = stripeAccountRepository;
        this.bundleSubscriptionRepository = bundleSubscriptionRepository;
        this.bundlePayoutService = bundlePayoutService;
        this.bundleSubscriptionService = bundleSubscriptionService;
    }

    @Transactional
    public void handleCheckoutSessionCompleted(Event event) {
        EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();

        if (dataObjectDeserializer.getObject().isEmpty()) {
            log.error("Unable to deserialize checkout.session.completed event");
            return;
        }

        StripeObject stripeObject = dataObjectDeserializer.getObject().get();
        if (!(stripeObject instanceof Session session)) {
            log.error("Event object is not a Session");
            return;
        }

        // Only subscription checkouts exist since M6 retired the weekly-reservation flow, but
        // this deliberately stays a mode check rather than becoming an unconditional call.
        // The Stripe account still holds historical mode=payment events from before the
        // cutover, and anyone resending one must get a quiet no-op — falling through would
        // hand it to the subscription handler, which cannot resolve it and would 500, putting
        // Stripe into a retry cycle for an event that can never succeed.
        if (!SUBSCRIPTION_MODE.equals(session.getMode())) {
            log.info("Ignoring checkout.session.completed in mode '{}' for session {} — the "
                            + "one-time reservation payment flow was removed in P1 M6",
                    session.getMode(), session.getId());
            return;
        }

        handleSubscriptionCheckoutCompleted(session);
    }

    @Transactional
    public void handleAccountUpdated(Event event) {
        EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
        if (dataObjectDeserializer.getObject().isEmpty()) {
            log.error("Unable to deserialize account.updated event");
            return;
        }

        StripeObject stripeObject = dataObjectDeserializer.getObject().get();
        if (!(stripeObject instanceof Account account)) {
            log.error("Event object is not an Account");
            return;
        }

        String stripeAccountId = account.getId();
        log.info("Processing account.updated event for Stripe account: {}", stripeAccountId);

        Optional<StripeAccount> localAccountOpt = stripeAccountRepository.findByStripeAccountId(stripeAccountId);

        if (localAccountOpt.isEmpty()) {
            log.warn("Received account.updated event for an unknown Stripe account: {}", stripeAccountId);
            return;
        }

        StripeAccount localAccount = localAccountOpt.get();
        boolean originalOnboardingComplete = localAccount.isOnboardingComplete();

        // Update our local record with the latest status from Stripe
        localAccount.setOnboardingComplete(Boolean.TRUE.equals(account.getDetailsSubmitted()));
        localAccount.setChargesEnabled(Boolean.TRUE.equals(account.getChargesEnabled()));
        localAccount.setPayoutsEnabled(Boolean.TRUE.equals(account.getPayoutsEnabled()));

        stripeAccountRepository.save(localAccount);

        log.info(
            "Updated local Stripe account {}: onboardingComplete={}, chargesEnabled={}, payoutsEnabled={}",
            stripeAccountId,
            localAccount.isOnboardingComplete(),
            localAccount.isChargesEnabled(),
            localAccount.isPayoutsEnabled()
        );

        if (!originalOnboardingComplete && localAccount.isOnboardingComplete()) {
            log.info("Account {} is now fully onboarded.", stripeAccountId);
        }
    }

    // ---------------------------------------------------------------------------
    // Bundle subscription handlers (M5)
    // ---------------------------------------------------------------------------

    /**
     * Links the Stripe subscription to our row and activates it.
     *
     * <p>Idempotent, and must stay that way: {@code invoice.paid} reliably arrives
     * first and links the row itself, so by the time this runs the row is usually
     * already ACTIVE and already linked.
     */
    private void handleSubscriptionCheckoutCompleted(Session session) {
        String sessionId = session.getId();
        String stripeSubscriptionId = session.getSubscription();

        log.info("Subscription checkout completed: sessionId={}, stripeSubscriptionId={}",
                sessionId, stripeSubscriptionId);

        Optional<BundleSubscription> subscriptionOpt =
                bundleSubscriptionRepository.findByStripeCheckoutSessionId(sessionId);

        if (subscriptionOpt.isEmpty()) {
            // Expected for an abandoned checkout that was later completed: the retry
            // overwrote stripe_checkout_session_id, so this session no longer maps to
            // a row. Log and no-op, per the standard idempotency pattern.
            log.warn("No bundle subscription found for checkout session: {}", sessionId);
            return;
        }

        BundleSubscription subscription = subscriptionOpt.get();

        if (subscription.getStatus() == BundleSubscriptionStatus.CANCELED) {
            log.warn("Ignoring checkout completion for already-canceled subscription {}",
                    subscription.getSubscriptionId());
            return;
        }

        // Captured before the overwrite: only a first activation (was INCOMPLETE) is a "new
        // subscription" worth emailing media owners about. In the normal case invoice.paid has
        // already flipped this row to ACTIVE by the time this handler runs, so this is a no-op.
        BundleSubscriptionStatus previousStatus = subscription.getStatus();

        subscription.setStripeSubscriptionId(stripeSubscriptionId);
        subscription.setStatus(BundleSubscriptionStatus.ACTIVE);
        bundleSubscriptionRepository.save(subscription);

        log.info("Bundle subscription {} is now ACTIVE (stripeSubscriptionId={})",
                subscription.getSubscriptionId(), stripeSubscriptionId);

        if (previousStatus == BundleSubscriptionStatus.INCOMPLETE) {
            notifyMediaOwnersOfNewSubscription(subscription.getSubscriptionId());
        }
    }

    /**
     * Activates the subscription for the paid period and pays the media owners.
     *
     * <p>Unlike every other handler here, this one <strong>throws</strong> rather than
     * no-opping when it cannot resolve a local row. {@code invoice.paid} reliably
     * arrives BEFORE {@code checkout.session.completed} — confirmed live on this
     * account, not theoretical — so a no-op would silently drop the first month's
     * payout. The 500 tells Stripe to redeliver.
     *
     * <p>In practice the throw is a backstop rather than the normal path: the
     * subscription's Stripe metadata carries our own {@code subscriptionId} (written
     * by the subscribe flow), so the row resolves on the first delivery and this
     * handler links {@code stripe_subscription_id} itself. (Decision D33.)
     */
    @Transactional
    public void handleInvoicePaid(Event event) {
        // A payload we cannot read is the same failure as a row we cannot find: it
        // would silently drop a payout. Throw so Stripe redelivers.
        Invoice invoice = requireObject(event, Invoice.class, "invoice.paid");

        Invoice.Parent.SubscriptionDetails details = subscriptionDetailsOf(invoice);
        if (details == null) {
            // A non-subscription invoice. Not ours, not payout-critical — no-op.
            log.debug("Ignoring invoice.paid for non-subscription invoice {}", invoice.getId());
            return;
        }

        String stripeSubscriptionId = details.getSubscription();
        String localSubscriptionId = details.getMetadata() == null
                ? null : details.getMetadata().get("subscriptionId");

        log.info("Invoice paid: invoice={}, stripeSubscription={}, localSubscription={}",
                invoice.getId(), stripeSubscriptionId, localSubscriptionId);

        BundleSubscription subscription =
                resolveSubscription(localSubscriptionId, stripeSubscriptionId)
                        .orElseThrow(() -> new BundleSubscriptionNotLinkedException(
                                stripeSubscriptionId, invoice.getId()));

        if (isOrphanedDuplicate(subscription, stripeSubscriptionId, "invoice.paid " + invoice.getId())) {
            return;
        }

        if (subscription.getStatus() == BundleSubscriptionStatus.CANCELED) {
            // Never resurrect a canceled subscription on a late or replayed event.
            log.warn("Ignoring invoice.paid for canceled subscription {} (invoice {})",
                    subscription.getSubscriptionId(), invoice.getId());
            return;
        }

        if (subscription.getStripeSubscriptionId() == null) {
            log.info("Linking subscription {} to {} from invoice.paid — this event won the race "
                    + "against checkout.session.completed",
                    subscription.getSubscriptionId(), stripeSubscriptionId);
            subscription.setStripeSubscriptionId(stripeSubscriptionId);
        }

        // Captured before the overwrite: only a first activation (was INCOMPLETE) is a "new
        // subscription" worth emailing media owners about — a renewal or a PAST_DUE recovery
        // payment reaches this same line every cycle and must not re-send it.
        BundleSubscriptionStatus previousStatus = subscription.getStatus();

        subscription.setStatus(BundleSubscriptionStatus.ACTIVE);
        LocalDateTime periodEnd = periodEndOf(invoice);
        if (periodEnd != null) {
            subscription.setCurrentPeriodEnd(periodEnd);
        }
        bundleSubscriptionRepository.save(subscription);

        bundlePayoutService.payOutInvoice(invoice.getId(), subscription.getSubscriptionId());

        // Sent last and deliberately outside payout's failure path: this handler is
        // @Transactional and throws on failure so Stripe redelivers. If the notification ran
        // before the payout and the payout then threw, the transaction would roll back to
        // INCOMPLETE, Stripe would redeliver, and the owner would be emailed twice for one
        // subscription. Sending last means the only failure mode is "no email yet", which
        // redelivery still fixes.
        if (previousStatus == BundleSubscriptionStatus.INCOMPLETE) {
            notifyMediaOwnersOfNewSubscription(subscription.getSubscriptionId());
        }
    }

    /**
     * Best-effort by construction ({@link BundleSubscriptionService#notifyMediaOwnersOfNewSubscription}
     * already catches and logs per-owner failures) — this outer guard exists only so an
     * unexpected exception from the lookup itself cannot turn a successful activation/payout
     * into a failed, retried webhook.
     */
    private void notifyMediaOwnersOfNewSubscription(String subscriptionId) {
        try {
            bundleSubscriptionService.notifyMediaOwnersOfNewSubscription(subscriptionId);
        } catch (Exception e) {
            log.error("Failed to send new-subscription notification for {}", subscriptionId, e);
        }
    }

    @Transactional
    public void handleInvoicePaymentFailed(Event event) {
        Optional<Invoice> invoiceOpt = optionalObject(event, Invoice.class, "invoice.payment_failed");
        if (invoiceOpt.isEmpty()) {
            return;
        }
        Invoice invoice = invoiceOpt.get();

        Invoice.Parent.SubscriptionDetails details = subscriptionDetailsOf(invoice);
        if (details == null) {
            log.debug("Ignoring invoice.payment_failed for non-subscription invoice {}", invoice.getId());
            return;
        }

        String localSubscriptionId = details.getMetadata() == null
                ? null : details.getMetadata().get("subscriptionId");

        Optional<BundleSubscription> subscriptionOpt =
                resolveSubscription(localSubscriptionId, details.getSubscription());

        if (subscriptionOpt.isEmpty()) {
            log.warn("No bundle subscription found for failed invoice {} (subscription {})",
                    invoice.getId(), details.getSubscription());
            return;
        }

        BundleSubscription subscription = subscriptionOpt.get();

        if (isOrphanedDuplicate(subscription, details.getSubscription(),
                "invoice.payment_failed " + invoice.getId())) {
            return;
        }

        if (subscription.getStatus() == BundleSubscriptionStatus.CANCELED) {
            log.warn("Ignoring invoice.payment_failed for canceled subscription {}",
                    subscription.getSubscriptionId());
            return;
        }

        subscription.setStatus(BundleSubscriptionStatus.PAST_DUE);
        bundleSubscriptionRepository.save(subscription);

        log.info("Bundle subscription {} marked PAST_DUE after failed invoice {}",
                subscription.getSubscriptionId(), invoice.getId());
    }

    @Transactional
    public void handleSubscriptionDeleted(Event event) {
        Optional<Subscription> stripeSubOpt =
                optionalObject(event, Subscription.class, "customer.subscription.deleted");
        if (stripeSubOpt.isEmpty()) {
            return;
        }
        Subscription stripeSub = stripeSubOpt.get();

        Optional<BundleSubscription> subscriptionOpt = resolveSubscription(
                metadataSubscriptionId(stripeSub), stripeSub.getId());

        if (subscriptionOpt.isEmpty()) {
            log.warn("No bundle subscription found for deleted Stripe subscription {}", stripeSub.getId());
            return;
        }

        BundleSubscription subscription = subscriptionOpt.get();

        // Critical here: cancelling an orphan in Stripe emits this event carrying the
        // live subscription's own metadata, so without this guard tidying up a
        // duplicate would cancel the paying subscription it was duplicating.
        if (isOrphanedDuplicate(subscription, stripeSub.getId(),
                "customer.subscription.deleted")) {
            return;
        }

        subscription.setStatus(BundleSubscriptionStatus.CANCELED);
        subscription.setCanceledAt(LocalDateTime.now());
        bundleSubscriptionRepository.save(subscription);

        log.info("Bundle subscription {} is now CANCELED (Stripe subscription {} ended)",
                subscription.getSubscriptionId(), stripeSub.getId());
    }

    /**
     * Syncs period end and the cancel flag. Covers changes made directly in the
     * Stripe Dashboard as well as our own cancel endpoint.
     */
    @Transactional
    public void handleSubscriptionUpdated(Event event) {
        Optional<Subscription> stripeSubOpt =
                optionalObject(event, Subscription.class, "customer.subscription.updated");
        if (stripeSubOpt.isEmpty()) {
            return;
        }
        Subscription stripeSub = stripeSubOpt.get();

        Optional<BundleSubscription> subscriptionOpt = resolveSubscription(
                metadataSubscriptionId(stripeSub), stripeSub.getId());

        if (subscriptionOpt.isEmpty()) {
            log.warn("No bundle subscription found for updated Stripe subscription {}", stripeSub.getId());
            return;
        }

        BundleSubscription subscription = subscriptionOpt.get();

        if (isOrphanedDuplicate(subscription, stripeSub.getId(), "customer.subscription.updated")) {
            return;
        }

        if (subscription.getStatus() == BundleSubscriptionStatus.CANCELED) {
            // Consistent with the other handlers: a canceled subscription is terminal,
            // and a late update must not rewrite its renewal date or cancel flag.
            log.warn("Ignoring subscription update for canceled subscription {}",
                    subscription.getSubscriptionId());
            return;
        }

        subscription.setCancelAtPeriodEnd(Boolean.TRUE.equals(stripeSub.getCancelAtPeriodEnd()));

        LocalDateTime periodEnd = currentPeriodEndOf(stripeSub);
        if (periodEnd != null) {
            subscription.setCurrentPeriodEnd(periodEnd);
        }
        bundleSubscriptionRepository.save(subscription);

        log.info("Synced bundle subscription {} from Stripe: cancelAtPeriodEnd={}, currentPeriodEnd={}",
                subscription.getSubscriptionId(), subscription.isCancelAtPeriodEnd(), periodEnd);
    }

    /**
     * True when the resolved row belongs to a <em>different</em> Stripe subscription
     * than the event does — i.e. the event came from an orphaned duplicate.
     *
     * <p>Duplicates arise from an abandoned checkout completed after the fact, which
     * mints its own Stripe subscription carrying the same {@code subscriptionId}
     * metadata (see the retry fix in {@code BundleSubscriptionServiceImpl}). Because
     * every handler here resolves by that metadata first, an orphan's event would
     * otherwise be applied to the <em>live</em> subscription's row: cancelling it,
     * marking it past due, or rewriting its renewal date. This check must therefore
     * run in every handler, not just the payout one.
     *
     * <p>Callers return quietly rather than throwing. Retrying cannot fix a duplicate,
     * and throwing would produce a retry storm repeating every billing cycle.
     */
    private boolean isOrphanedDuplicate(BundleSubscription subscription,
            String stripeSubscriptionId, String eventDescription) {

        if (subscription.getStripeSubscriptionId() == null
                || subscription.getStripeSubscriptionId().equals(stripeSubscriptionId)) {
            return false;
        }

        log.error("ORPHANED STRIPE SUBSCRIPTION: {} arrived for {}, but local subscription {} is linked "
                        + "to {}. Ignoring — acting on it would corrupt the live subscription's state. "
                        + "Cancel the orphan in Stripe.",
                eventDescription, stripeSubscriptionId,
                subscription.getSubscriptionId(), subscription.getStripeSubscriptionId());
        return true;
    }

    /**
     * Resolves the local row by our own id first, falling back to Stripe's.
     *
     * <p>Our id comes from the subscription's Stripe metadata, written when the
     * checkout session was created, so it is present from the very first event —
     * whereas {@code stripe_subscription_id} is only populated once one of the
     * handlers has linked it.
     */
    private Optional<BundleSubscription> resolveSubscription(
            String localSubscriptionId, String stripeSubscriptionId) {

        if (localSubscriptionId != null && !localSubscriptionId.isBlank()) {
            Optional<BundleSubscription> byLocalId =
                    bundleSubscriptionRepository.findBySubscriptionId(localSubscriptionId);
            if (byLocalId.isPresent()) {
                return byLocalId;
            }
            log.warn("Subscription metadata carried subscriptionId {} but no such row exists", localSubscriptionId);
        }

        if (stripeSubscriptionId == null || stripeSubscriptionId.isBlank()) {
            return Optional.empty();
        }
        return bundleSubscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId);
    }

    private String metadataSubscriptionId(Subscription stripeSub) {
        return stripeSub.getMetadata() == null ? null : stripeSub.getMetadata().get("subscriptionId");
    }

    private Invoice.Parent.SubscriptionDetails subscriptionDetailsOf(Invoice invoice) {
        // stripe-java 31.x: an invoice no longer carries `subscription` directly —
        // it hangs off `parent.subscription_details`. Verified against live payloads.
        return invoice.getParent() == null ? null : invoice.getParent().getSubscriptionDetails();
    }

    /**
     * The end of the period this invoice paid for.
     *
     * <p>Read from the line item, not {@code invoice.period_end}: on a real first
     * invoice from this account both invoice-level period fields equal the creation
     * timestamp, which would set the renewal date to today.
     */
    private LocalDateTime periodEndOf(Invoice invoice) {
        if (invoice.getLines() == null || invoice.getLines().getData() == null
                || invoice.getLines().getData().isEmpty()) {
            return null;
        }
        InvoiceLineItem line = invoice.getLines().getData().get(0);
        return line.getPeriod() == null ? null : toLocalDateTime(line.getPeriod().getEnd());
    }

    /**
     * stripe-java 31.x moved {@code current_period_end} off the Subscription and onto
     * its items, so it is read from the first item rather than the subscription.
     */
    private LocalDateTime currentPeriodEndOf(Subscription stripeSub) {
        if (stripeSub.getItems() == null || stripeSub.getItems().getData() == null
                || stripeSub.getItems().getData().isEmpty()) {
            return null;
        }
        return toLocalDateTime(stripeSub.getItems().getData().get(0).getCurrentPeriodEnd());
    }

    private static LocalDateTime toLocalDateTime(Long epochSeconds) {
        return epochSeconds == null
                ? null
                : LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.systemDefault());
    }

    /** Deserializes or throws — for handlers where a dropped event loses money. */
    private <T extends StripeObject> T requireObject(Event event, Class<T> type, String eventType) {
        Optional<StripeObject> object = event.getDataObjectDeserializer().getObject();
        if (object.isEmpty()) {
            throw new IllegalStateException("Unable to deserialize " + eventType
                    + " event; returning 500 so Stripe redelivers it");
        }
        if (!type.isInstance(object.get())) {
            throw new IllegalStateException(eventType + " event object is not a " + type.getSimpleName());
        }
        return type.cast(object.get());
    }

    /** Deserializes or logs and gives up — for handlers where a dropped event is harmless. */
    private <T extends StripeObject> Optional<T> optionalObject(Event event, Class<T> type, String eventType) {
        Optional<StripeObject> object = event.getDataObjectDeserializer().getObject();
        if (object.isEmpty()) {
            log.error("Unable to deserialize {} event", eventType);
            return Optional.empty();
        }
        if (!type.isInstance(object.get())) {
            log.error("{} event object is not a {}", eventType, type.getSimpleName());
            return Optional.empty();
        }
        return Optional.of(type.cast(object.get()));
    }

}
