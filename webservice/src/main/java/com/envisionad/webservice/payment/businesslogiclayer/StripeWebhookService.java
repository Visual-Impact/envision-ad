package com.envisionad.webservice.payment.businesslogiclayer;

import com.envisionad.webservice.advertisement.businesslogiclayer.AdCampaignService;
import com.envisionad.webservice.advertisement.dataaccesslayer.AdCampaign;
import com.envisionad.webservice.advertisement.dataaccesslayer.AdCampaignRepository;
import com.envisionad.webservice.advertisement.exceptions.AdCampaignNotFoundException;
import com.envisionad.webservice.business.dataaccesslayer.Employee;
import com.envisionad.webservice.business.dataaccesslayer.EmployeeRepository;
import com.envisionad.webservice.config.Auth0Service;
import com.envisionad.webservice.media.DataAccessLayer.Media;
import com.envisionad.webservice.media.DataAccessLayer.MediaRepository;
import com.envisionad.webservice.media.exceptions.MediaNotFoundException;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscription;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionRepository;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionStatus;
import com.envisionad.webservice.payment.dataaccesslayer.PaymentIntent;
import com.envisionad.webservice.payment.dataaccesslayer.PaymentIntentRepository;
import com.envisionad.webservice.payment.dataaccesslayer.PaymentStatus;
import com.envisionad.webservice.payment.dataaccesslayer.StripeAccount;
import com.envisionad.webservice.payment.dataaccesslayer.StripeAccountRepository;
import com.envisionad.webservice.payment.exceptions.BundleSubscriptionNotLinkedException;
import com.envisionad.webservice.reservation.dataaccesslayer.Reservation;
import com.envisionad.webservice.reservation.dataaccesslayer.ReservationRepository;
import com.envisionad.webservice.reservation.dataaccesslayer.ReservationStatus;
import com.envisionad.webservice.utils.EmailService;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class StripeWebhookService {

    /** Stripe Checkout mode marking a bundle subscription rather than a legacy one-time payment. */
    private static final String SUBSCRIPTION_MODE = "subscription";

    private final PaymentIntentRepository paymentIntentRepository;
    private final ReservationRepository reservationRepository;
    private final EmailService emailService;
    private final EmployeeRepository employeeRepository;
    private final MediaRepository mediaRepository;
    private final AdCampaignRepository adCampaignRepository;
    private final AdCampaignService adCampaignService;
    private final StripeAccountRepository stripeAccountRepository;
    private final Auth0Service auth0Service;
    private final BundleSubscriptionRepository bundleSubscriptionRepository;
    private final BundlePayoutService bundlePayoutService;


    public StripeWebhookService(PaymentIntentRepository paymentIntentRepository,
                                ReservationRepository reservationRepository, EmailService emailService, EmployeeRepository employeeRepository, MediaRepository mediaRepository, AdCampaignRepository adCampaignRepository, AdCampaignService adCampaignService, StripeAccountRepository stripeAccountRepository, Auth0Service auth0Service, BundleSubscriptionRepository bundleSubscriptionRepository, BundlePayoutService bundlePayoutService) {
        this.paymentIntentRepository = paymentIntentRepository;
        this.reservationRepository = reservationRepository;
        this.emailService = emailService;
        this.employeeRepository = employeeRepository;
        this.mediaRepository = mediaRepository;
        this.adCampaignRepository = adCampaignRepository;
        this.adCampaignService = adCampaignService;
        this.stripeAccountRepository = stripeAccountRepository;
        this.auth0Service = auth0Service;
        this.bundleSubscriptionRepository = bundleSubscriptionRepository;
        this.bundlePayoutService = bundlePayoutService;
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

        // Subscription checkouts and the legacy one-time reservation checkouts arrive
        // as the same event type and are told apart by mode. The brief puts this branch
        // in WebhookController, but the controller would have to deserialize the event
        // itself to see the mode — duplicating the work above — so it lives here where
        // the Session is already in hand. (Decision D34.)
        if (SUBSCRIPTION_MODE.equals(session.getMode())) {
            handleSubscriptionCheckoutCompleted(session);
            return;
        }

        String sessionId = session.getId();
        String paymentIntentId = session.getPaymentIntent();

        log.info("Checkout session completed: sessionId={}, paymentIntentId={}",
                sessionId, paymentIntentId);

        // Update our payment record with the PaymentIntent ID
        Optional<PaymentIntent> paymentOpt = paymentIntentRepository.findByStripeSessionId(sessionId);

        if (paymentOpt.isEmpty()) {
            log.warn("No payment record found for session: {}", sessionId);
            return;
        }

        PaymentIntent payment = paymentOpt.get();
        payment.setStripePaymentIntentId(paymentIntentId);
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setUpdatedAt(LocalDateTime.now());
        paymentIntentRepository.save(payment);

        log.info("Updated payment record: reservationId={}, status=SUCCEEDED",
                payment.getReservationId());

        // Update reservation status to CONFIRMED
        updateReservationStatus(payment.getReservationId(), ReservationStatus.CONFIRMED);
    }

    @Transactional
    public void handlePaymentIntentSucceeded(Event event) {
        EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();

        if (dataObjectDeserializer.getObject().isEmpty()) {
            log.error("Unable to deserialize payment_intent.succeeded event");
            return;
        }

        StripeObject stripeObject = dataObjectDeserializer.getObject().get();
        if (!(stripeObject instanceof com.stripe.model.PaymentIntent stripePaymentIntent)) {
            log.error("Event object is not a PaymentIntent");
            return;
        }

        String paymentIntentId = stripePaymentIntent.getId();

        log.info("Payment intent succeeded: paymentIntentId={}", paymentIntentId);

        // Find and update payment record
        Optional<PaymentIntent> paymentOpt = paymentIntentRepository.findByStripePaymentIntentId(paymentIntentId);

        if (paymentOpt.isPresent()) {
            PaymentIntent payment = paymentOpt.get();
            payment.setStatus(PaymentStatus.SUCCEEDED);
            payment.setUpdatedAt(LocalDateTime.now());
            paymentIntentRepository.save(payment);

            log.info("Updated payment record from webhook: reservationId={}",
                    payment.getReservationId());

            // Update reservation status to CONFIRMED
            updateReservationStatus(payment.getReservationId(), ReservationStatus.CONFIRMED);
        } else {
            log.warn("No payment record found for PaymentIntent: {}", paymentIntentId);
        }
    }

    @Transactional
    public void handlePaymentIntentFailed(Event event) {
        EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();

        if (dataObjectDeserializer.getObject().isEmpty()) {
            log.error("Unable to deserialize payment_intent.payment_failed event");
            return;
        }

        StripeObject stripeObject = dataObjectDeserializer.getObject().get();
        if (!(stripeObject instanceof com.stripe.model.PaymentIntent stripePaymentIntent)) {
            log.error("Event object is not a PaymentIntent");
            return;
        }

        String paymentIntentId = stripePaymentIntent.getId();

        log.info("Payment intent failed: paymentIntentId={}", paymentIntentId);

        // Find and update payment record
        Optional<PaymentIntent> paymentOpt = paymentIntentRepository.findByStripePaymentIntentId(paymentIntentId);

        if (paymentOpt.isPresent()) {
            PaymentIntent payment = paymentOpt.get();
            payment.setStatus(PaymentStatus.FAILED);
            payment.setUpdatedAt(LocalDateTime.now());
            paymentIntentRepository.save(payment);

            log.info("Marked payment as failed: reservationId={}", payment.getReservationId());

            // Update reservation status to CANCELLED due to payment failure
            updateReservationStatus(payment.getReservationId(), ReservationStatus.CANCELLED);
        } else {
            log.warn("No payment record found for PaymentIntent: {}", paymentIntentId);
        }
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

        subscription.setStripeSubscriptionId(stripeSubscriptionId);
        subscription.setStatus(BundleSubscriptionStatus.ACTIVE);
        bundleSubscriptionRepository.save(subscription);

        log.info("Bundle subscription {} is now ACTIVE (stripeSubscriptionId={})",
                subscription.getSubscriptionId(), stripeSubscriptionId);
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

        // A row already linked to a DIFFERENT live Stripe subscription means an
        // orphaned duplicate exists in Stripe — an abandoned checkout that was
        // completed later and minted its own subscription. Paying it out would pay
        // the same owners twice over for one bundle. Log loudly and stop; do NOT
        // throw, because retrying cannot fix it and would produce a retry storm that
        // repeats every billing cycle forever.
        if (subscription.getStripeSubscriptionId() != null
                && !subscription.getStripeSubscriptionId().equals(stripeSubscriptionId)) {
            log.error("ORPHANED STRIPE SUBSCRIPTION: invoice {} belongs to {}, but local subscription {} "
                            + "is linked to {}. The customer is being billed more than once for this bundle. "
                            + "No payout made — cancel the orphan in Stripe.",
                    invoice.getId(), stripeSubscriptionId,
                    subscription.getSubscriptionId(), subscription.getStripeSubscriptionId());
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

        subscription.setStatus(BundleSubscriptionStatus.ACTIVE);
        LocalDateTime periodEnd = periodEndOf(invoice);
        if (periodEnd != null) {
            subscription.setCurrentPeriodEnd(periodEnd);
        }
        bundleSubscriptionRepository.save(subscription);

        bundlePayoutService.payOutInvoice(invoice.getId(), subscription.getSubscriptionId());
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

    @Transactional
    public void updateReservationStatus(String reservationId, ReservationStatus newStatus) {
        Optional<Reservation> reservationOpt = reservationRepository.findByReservationId(reservationId);

        if (reservationOpt.isEmpty()) {
            log.warn("Reservation not found for payment update: {}", reservationId);
            return;
        }

        Reservation reservation = reservationOpt.get();
        ReservationStatus oldStatus = reservation.getStatus();

        // Only update if status is changing
        if (oldStatus != newStatus) {
            reservation.setStatus(newStatus);
            reservationRepository.save(reservation);
            log.info("Updated reservation status: id={}, {} -> {}",
                    reservationId, oldStatus, newStatus);
            if (newStatus == ReservationStatus.CONFIRMED) {
                Media media = mediaRepository.findById(reservation.getMediaId())
                        .orElseThrow(() -> new MediaNotFoundException(reservation.getMediaId().toString()));
                AdCampaign campaign = adCampaignRepository.findByCampaignId_CampaignId(reservation.getCampaignId());
                if (campaign == null) {
                    throw new AdCampaignNotFoundException(reservation.getCampaignId());
                }
                BigDecimal totalPrice = reservation.getTotalPrice();
                if(totalPrice == null) {
                    totalPrice = BigDecimal.ZERO;
                }
                sendNotificationEmails(media, reservation, campaign, totalPrice);
            }
        } else {
            log.debug("Reservation already has status {}: {}", newStatus, reservationId);
        }
    }

    private void sendNotificationEmails(Media media, Reservation reservation,
                                        AdCampaign campaign, BigDecimal totalPrice) {
        String mediaOwnerBusinessId = media.getBusinessId().toString();
        List<Employee> mediaOwners = employeeRepository.findAllByBusinessId_BusinessId(mediaOwnerBusinessId);
        List<String> mediaOwnerEmailAddresses = mediaOwners.stream()
                .map(Employee::getUserId)
                .filter(uid -> uid != null && !uid.isBlank())
                .distinct()
                .map(uid -> {
                    try {
                        return auth0Service.getUserEmailByUserId(uid);
                    } catch (Exception e) {
                        log.warn("Failed to fetch email for userId {} from Auth0: {}", uid, e.getMessage());
                        return null;
                    }
                })
                .filter(email -> email != null && !email.isEmpty())
                .toList();

        if (!mediaOwnerEmailAddresses.isEmpty()) {
            for (String ownerEmailAddress : mediaOwnerEmailAddresses) {
                sendReservationEmail(ownerEmailAddress, media, reservation, campaign, totalPrice);
            }
        } else {
            log.warn("No email found for media owner in business: {}", mediaOwnerBusinessId);
        }
    }

    private void sendReservationEmail(String ownerEmail, Media media, Reservation reservation,
                                      AdCampaign campaign, BigDecimal totalPrice) {
        try {
            List<String> imageLinks = adCampaignService.getAllCampaignImageLinks(campaign.getCampaignId().getCampaignId());

            String previewSection;
            if (imageLinks == null || imageLinks.isEmpty()) {
                previewSection = "No preview images available.";
            } else {
                StringBuilder sb = new StringBuilder("Preview Images:")
                        .append(System.lineSeparator());
                for (String link : imageLinks) {
                    sb.append("- ")
                            .append(link)
                            .append(System.lineSeparator());
                }
                previewSection = sb.toString().trim();
            }

            String emailBody = String.format(
                    "A new reservation has been created for your media%n" +
                            "Media Name: %s%n" +
                            "Ad Campaign Name: %s%n" +
                            "Total Price: $%.2f%n" +
                            "%s",
                    media.getTitle(), campaign.getName(), totalPrice, previewSection
            );
            emailService.sendSimpleEmail(ownerEmail, "New Reservation Created", emailBody);
        } catch (Exception e) {
            // Log error instead of throwing exception to avoid failing reservation creation
            log.error("Failed to send reservation notification email for reservation: {} to owner: {}",
                    reservation.getReservationId(), ownerEmail, e);
        }
    }
}
