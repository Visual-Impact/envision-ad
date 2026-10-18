package com.envisionad.webservice.payment.businesslogiclayer;

import com.envisionad.webservice.payment.dataaccesslayer.*;
import com.envisionad.webservice.payment.exceptions.PayoutFundingLookupException;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.InvoicePayment;
import com.stripe.model.InvoicePaymentCollection;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Transfer;
import com.stripe.net.RequestOptions;
import com.stripe.param.InvoicePaymentListParams;
import com.stripe.param.TransferCreateParams;
import com.stripe.param.TransferListParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Splits a paid subscription invoice across the media owners whose screens the
 * bundle covers.
 *
 * <p>This cannot use {@code transfer_data.destination} the way the legacy
 * single-owner reservation checkout does: that mechanism sends one charge to one
 * connected account, and a bundle spans many owners. The platform is merchant of
 * record for the whole subscription charge, and owner shares go out afterwards as
 * separate transfers.
 */
@Slf4j
@Service
public class BundlePayoutServiceImpl implements BundlePayoutService {

    private static final int MONEY_SCALE = 2;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    /** Stripe's error code for a transfer the platform's available balance cannot cover. */
    static final String INSUFFICIENT_FUNDS = "balance_insufficient";

    /**
     * The minimum gap between two attempts on one payout. Stripe keeps the result of an
     * idempotent request for at least 24 hours and replays it, error included, so a retry
     * with the same key inside that window can only fail again.
     */
    private static final Duration MIN_RETRY_GAP = Duration.ofHours(24);

    /** Slack on the "already transferred?" search window, for clock skew against Stripe. */
    private static final Duration RECONCILE_SKEW = Duration.ofHours(1);

    @Value("${stripe.platform-fee-percent}")
    private int platformFeePercent;

    /** How long an insufficient-funds payout keeps being retried before it is left for a human. */
    @Value("${envision.payouts.retry.max-age}")
    private Duration retryMaxAge;

    /**
     * How long a share is held for an owner whose Connect onboarding is incomplete. Fixed at 90
     * days by the Terms of Service (§7, media owners' payouts); change both together.
     */
    @Value("${envision.payouts.hold.max-age}")
    private Duration holdMaxAge;

    private final BundleSubscriptionItemRepository itemRepository;
    private final StripeAccountRepository stripeAccountRepository;
    private final BundlePayoutRepository payoutRepository;

    public BundlePayoutServiceImpl(BundleSubscriptionItemRepository itemRepository,
            StripeAccountRepository stripeAccountRepository,
            BundlePayoutRepository payoutRepository) {
        this.itemRepository = itemRepository;
        this.stripeAccountRepository = stripeAccountRepository;
        this.payoutRepository = payoutRepository;
    }

    @Override
    public void payOutInvoice(String stripeInvoiceId, String subscriptionId) {
        List<BundleSubscriptionItem> items = itemRepository.findAllBySubscriptionId(subscriptionId);

        if (items.isEmpty()) {
            log.warn("Invoice {} paid for subscription {} but it has no items — nothing to pay out",
                    stripeInvoiceId, subscriptionId);
            return;
        }

        Map<String, BigDecimal> sharesByOwner = sumByOwner(items);

        // The idempotency guard. Any existing row — paid, skipped or failed — means this owner
        // has already been handled for this invoice. Redelivery is routine here: invoice.paid
        // throws on lookup miss specifically to make Stripe retry.
        List<String> unhandledOwners = new ArrayList<>();
        for (String ownerBusinessId : sharesByOwner.keySet()) {
            if (payoutRepository.existsByStripeInvoiceIdAndMediaOwnerBusinessId(stripeInvoiceId, ownerBusinessId)) {
                log.info("Owner {} already handled for invoice {} — skipping (redelivered event)",
                        ownerBusinessId, stripeInvoiceId);
            } else {
                unhandledOwners.add(ownerBusinessId);
            }
        }
        if (unhandledOwners.isEmpty()) {
            return;
        }

        log.info("Paying out invoice {} for subscription {}: {} screens across {} owner(s)",
                stripeInvoiceId, subscriptionId, items.size(), sharesByOwner.size());

        // Decided from EVERY owner's share, not just the unhandled ones, so a redelivery after
        // a partial run picks the same funding as the first delivery did.
        String sourceChargeId = sourceChargeFor(stripeInvoiceId, totalNetOf(sharesByOwner));

        for (String ownerBusinessId : unhandledOwners) {
            payOutOwner(stripeInvoiceId, subscriptionId, ownerBusinessId,
                    sharesByOwner.get(ownerBusinessId), sourceChargeId);
        }
    }

    /**
     * Sums each owner's frozen screen prices <em>as they are stored</em>.
     *
     * <p>Deliberately does NOT scale by the bundle's discount. The items hold each
     * screen's full undiscounted {@code media.price} while the subscription's
     * {@code monthlyAmount} is the discounted amount actually charged — the platform
     * absorbs discounts out of its own fee so owners are paid as if there were no
     * promotion (decision D30). Scaling here would silently underpay every owner on
     * every discounted bundle.
     *
     * <p>Consequence, accepted rather than guarded: a discount larger than
     * {@code stripe.platform-fee-percent} pays out more than the invoice collected.
     * {@link #sourceChargeFor} handles that case by funding from the platform balance.
     *
     * <p>Insertion-ordered so payouts happen in a stable, reproducible order.
     */
    private Map<String, BigDecimal> sumByOwner(List<BundleSubscriptionItem> items) {
        Map<String, BigDecimal> byOwner = new LinkedHashMap<>();
        for (BundleSubscriptionItem item : items) {
            BigDecimal amount = item.getMonthlyAmount() == null ? BigDecimal.ZERO : item.getMonthlyAmount();
            byOwner.merge(item.getMediaOwnerBusinessId(), amount, BigDecimal::add);
        }
        return byOwner;
    }

    private BigDecimal netOf(BigDecimal grossAmount) {
        return grossAmount
                .multiply(BigDecimal.valueOf(100L - platformFeePercent))
                .divide(ONE_HUNDRED, MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** The sum of the rounded per-owner amounts: exactly what the transfers would add up to. */
    private BigDecimal totalNetOf(Map<String, BigDecimal> sharesByOwner) {
        return sharesByOwner.values().stream().map(this::netOf).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Picks how every transfer on this invoice is funded. One decision per invoice, because
     * Stripe caps the <em>sum</em> of all transfers sharing a {@code source_transaction} at
     * that charge's amount.
     *
     * <p><strong>Normal case: the owners' combined share fits inside the charge.</strong> Every
     * transfer names the charge as its {@code source_transaction}. Stripe accepts such a
     * transfer while the charge is still in the platform's <em>pending</em> balance, and
     * releases the funds to the owner when the charge settles. In live mode card payments stay
     * pending for days, so a transfer from the <em>available</em> balance made on
     * {@code invoice.paid} fails with {@code balance_insufficient}. (Sandbox hides this: test
     * card 4000 0000 0000 0077 credits the available balance directly.)
     *
     * <p><strong>D30 case: owners are owed more than the invoice collected.</strong> A bundle
     * discount larger than the platform fee does this, and so does a 100% coupon (no charge
     * at all). A {@code source_transaction} transfer would be rejected, so these come from the
     * platform's available balance, as every transfer did before. In live mode that often
     * fails at first. The row is then recorded as {@code balance_insufficient} and
     * {@link #retryInsufficientFundsPayouts} picks it up once funds have settled.
     *
     * @return the charge to use as {@code source_transaction}, or null to fund from the balance
     * @throws PayoutFundingLookupException if Stripe cannot be asked; nothing has moved yet,
     *         and the webhook's 500 makes Stripe redeliver
     */
    private String sourceChargeFor(String stripeInvoiceId, BigDecimal totalNet) {
        Optional<InvoiceCharge> charge = findInvoiceCharge(stripeInvoiceId);
        if (charge.isEmpty()) {
            log.warn("Invoice {} has no single charge to fund payouts from (zero-amount, paid out of band "
                    + "or in several payments) — paying {} from the platform balance", stripeInvoiceId, totalNet);
            return null;
        }
        if (toCents(totalNet) <= charge.get().amountInCents()) {
            return charge.get().id();
        }
        log.warn("Invoice {}: owners are owed {} but charge {} collected {} cents (a discount larger than "
                        + "the {}% platform fee) — paying from the platform balance",
                stripeInvoiceId, totalNet, charge.get().id(), charge.get().amountInCents(), platformFeePercent);
        return null;
    }

    private record InvoiceCharge(String id, long amountInCents) {
    }

    /**
     * The one charge that paid this invoice. Invoices carry no {@code charge} field on
     * current API versions; the link is the invoice's {@code InvoicePayment}, whose payment
     * is either a charge or a PaymentIntent (expanded here for its {@code latest_charge}).
     */
    private Optional<InvoiceCharge> findInvoiceCharge(String stripeInvoiceId) {
        InvoicePaymentCollection payments;
        try {
            payments = InvoicePayment.list(InvoicePaymentListParams.builder()
                    .setInvoice(stripeInvoiceId)
                    .setStatus(InvoicePaymentListParams.Status.PAID)
                    .addExpand("data.payment.payment_intent")
                    .build());
        } catch (StripeException e) {
            throw new PayoutFundingLookupException(stripeInvoiceId, e);
        }

        List<InvoicePayment> paid = payments.getData();
        if (paid == null || paid.size() != 1) {
            return Optional.empty();
        }
        InvoicePayment payment = paid.get(0);
        String chargeId = chargeIdOf(payment.getPayment());
        if (chargeId == null || payment.getAmountPaid() == null) {
            return Optional.empty();
        }
        return Optional.of(new InvoiceCharge(chargeId, payment.getAmountPaid()));
    }

    private String chargeIdOf(InvoicePayment.Payment payment) {
        if (payment == null) {
            return null;
        }
        if (payment.getCharge() != null) {
            return payment.getCharge();
        }
        PaymentIntent intent = payment.getPaymentIntentObject();
        return intent == null ? null : intent.getLatestCharge();
    }

    /**
     * One owner's transfer. Every failure mode is contained here: an owner who
     * cannot be paid is recorded and logged, and the loop continues to the next.
     */
    private void payOutOwner(String stripeInvoiceId, String subscriptionId,
            String ownerBusinessId, BigDecimal grossAmount, String sourceChargeId) {

        BigDecimal netAmount = netOf(grossAmount);

        if (netAmount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Owner {} share on invoice {} rounds to zero (gross {}) — nothing to transfer",
                    ownerBusinessId, stripeInvoiceId, grossAmount);
            record(stripeInvoiceId, subscriptionId, ownerBusinessId, grossAmount, netAmount,
                    BundlePayoutStatus.SKIPPED_ZERO_AMOUNT, null, null, null);
            return;
        }

        // "Complete" is the same test the media-creation gate and the Stripe page use: details
        // submitted, charges and payouts enabled. Details alone are submitted before Stripe has
        // verified the owner, and a transfer then would fail for good instead of waiting.
        Optional<StripeAccount> account = stripeAccountRepository.findByBusinessId(ownerBusinessId);
        if (account.isEmpty() || !StripeOnboardingState.isComplete(account.get())) {
            log.warn("Owner {} cannot be paid yet for invoice {} ({}): holding their {} share until "
                            + "onboarding completes, for up to {} days; other owners on this invoice are unaffected",
                    ownerBusinessId, stripeInvoiceId,
                    account.isEmpty() ? "no Stripe account" : "Connect onboarding incomplete", netAmount,
                    holdMaxAge.toDays());
            // The funding source is kept: it was decided counting this owner's share, so the
            // release must use it too (see sourceChargeFor).
            record(stripeInvoiceId, subscriptionId, ownerBusinessId, grossAmount, netAmount,
                    BundlePayoutStatus.HELD_NOT_ONBOARDED, null, sourceChargeId, null);
            return;
        }

        try {
            Transfer transfer = createTransfer(stripeInvoiceId, subscriptionId, ownerBusinessId,
                    account.get().getStripeAccountId(), netAmount, sourceChargeId);
            record(stripeInvoiceId, subscriptionId, ownerBusinessId, grossAmount, netAmount,
                    BundlePayoutStatus.PAID, transfer.getId(), sourceChargeId, null);
            log.info("Paid owner {} {} for invoice {} (gross {}, {}% platform fee, source {}): transfer {}",
                    ownerBusinessId, netAmount, stripeInvoiceId, grossAmount, platformFeePercent,
                    sourceChargeId == null ? "platform balance" : sourceChargeId, transfer.getId());
        } catch (StripeException e) {
            // Swallowed on purpose. Rethrowing would fail the webhook, and Stripe would
            // redeliver an event whose other transfers have already moved money.
            log.error("Transfer to owner {} for invoice {} was rejected by Stripe ({}): {}{}",
                    ownerBusinessId, stripeInvoiceId, e.getCode(), e.getMessage(),
                    INSUFFICIENT_FUNDS.equals(e.getCode()) ? " — will be retried once funds settle" : "", e);
            record(stripeInvoiceId, subscriptionId, ownerBusinessId, grossAmount, netAmount,
                    BundlePayoutStatus.FAILED, null, sourceChargeId, e);
        }
    }

    @Override
    public void retryInsufficientFundsPayouts() {
        LocalDateTime now = LocalDateTime.now();
        List<BundlePayout> due = payoutRepository.findRetryable(
                BundlePayoutStatus.FAILED, INSUFFICIENT_FUNDS, now.minus(MIN_RETRY_GAP), now.minus(retryMaxAge));
        if (due.isEmpty()) {
            return;
        }

        log.info("Retrying {} payout(s) that failed for insufficient funds", due.size());
        for (BundlePayout payout : due) {
            try {
                retry(payout);
            } catch (Exception e) {
                // One payout's failure (Stripe unreachable, say) must not strand the rest. The
                // row is untouched, so the next run tries it again.
                log.error("Retry of payout {} (invoice {}, owner {}) failed unexpectedly",
                        payout.getId(), payout.getStripeInvoiceId(), payout.getMediaOwnerBusinessId(), e);
            }
        }
    }

    /**
     * Retries one payout with the original idempotency key and the original parameters: the
     * same amount (the stored post-fee figure, never recomputed), the same funding source and
     * the same metadata. Stripe rejects a reused key sent with different parameters.
     *
     * <p>The key alone cannot prevent a double payment here. Stripe keeps keys for only about
     * 24 hours, and every retry happens after that. So before transferring, this asks Stripe
     * whether a transfer for this invoice and owner already exists, for example one whose
     * success was never recorded because the database write failed. If one exists, it is
     * recorded and nothing new is sent. Within the 24 hours the key still covers a concurrent
     * duplicate attempt.
     */
    private void retry(BundlePayout payout) throws StripeException {
        String ownerBusinessId = payout.getMediaOwnerBusinessId();
        Optional<StripeAccount> account = stripeAccountRepository.findByBusinessId(ownerBusinessId);
        if (account.isEmpty() || !account.get().isOnboardingComplete()) {
            log.warn("Not retrying payout {} for invoice {}: owner {} no longer has a usable Stripe account",
                    payout.getId(), payout.getStripeInvoiceId(), ownerBusinessId);
            return;
        }
        String destination = account.get().getStripeAccountId();

        Optional<Transfer> existing = findExistingTransfer(payout, destination);
        if (existing.isPresent()) {
            log.warn("Payout {} for invoice {} is recorded FAILED but Stripe already has transfer {} for it — "
                    + "recording that instead of transferring again", payout.getId(), payout.getStripeInvoiceId(),
                    existing.get().getId());
            markPaid(payout, existing.get().getId());
            return;
        }

        payout.setLastAttemptAt(LocalDateTime.now());
        try {
            Transfer transfer = createTransfer(payout.getStripeInvoiceId(), payout.getSubscriptionId(),
                    ownerBusinessId, destination, payout.getAmount(), payout.getStripeSourceChargeId());
            markPaid(payout, transfer.getId());
            log.info("Retried payout {}: paid owner {} {} for invoice {}: transfer {}", payout.getId(),
                    ownerBusinessId, payout.getAmount(), payout.getStripeInvoiceId(), transfer.getId());
        } catch (StripeException e) {
            // Any other code ends the retries for this row: it is no longer selected.
            log.warn("Retry of payout {} for invoice {} was rejected by Stripe ({}): {}", payout.getId(),
                    payout.getStripeInvoiceId(), e.getCode(), e.getMessage());
            payout.setFailureCode(e.getCode());
            payout.setFailureReason(e.getMessage());
            payoutRepository.save(payout);
        }
    }

    @Override
    public void releaseHeldPayouts(String ownerBusinessId) {
        try {
            List<BundlePayout> held = payoutRepository.findAllByMediaOwnerBusinessIdAndStatusOrderByCreatedAtAsc(
                    ownerBusinessId, BundlePayoutStatus.HELD_NOT_ONBOARDED);
            if (held.isEmpty()) {
                return;
            }
            Optional<StripeAccount> account = stripeAccountRepository.findByBusinessId(ownerBusinessId);
            if (account.isEmpty() || !StripeOnboardingState.isComplete(account.get())) {
                return;
            }
            releaseAll(held, account.get().getStripeAccountId());
        } catch (Exception e) {
            // The callers are a webhook and a status read; neither should fail over this. Every
            // held row is still HELD, so the hold sweep picks them up.
            log.error("Releasing held payouts for owner {} failed unexpectedly", ownerBusinessId, e);
        }
    }

    @Override
    public void releaseOrExpireHeldPayouts() {
        List<BundlePayout> held = payoutRepository.findAllByStatusOrderByCreatedAtAsc(
                BundlePayoutStatus.HELD_NOT_ONBOARDED);
        if (held.isEmpty()) {
            return;
        }

        LocalDateTime expireCreatedBefore = LocalDateTime.now().minus(holdMaxAge);
        Map<String, List<BundlePayout>> heldByOwner = new LinkedHashMap<>();
        for (BundlePayout payout : held) {
            heldByOwner.computeIfAbsent(payout.getMediaOwnerBusinessId(), owner -> new ArrayList<>()).add(payout);
        }

        log.info("Checking {} held payout(s) across {} owner(s)", held.size(), heldByOwner.size());
        for (Map.Entry<String, List<BundlePayout>> entry : heldByOwner.entrySet()) {
            try {
                releaseOrExpire(entry.getKey(), entry.getValue(), expireCreatedBefore);
            } catch (Exception e) {
                // Stripe unreachable, say. This owner's rows stay HELD and are looked at again
                // next run; nothing is expired on a guess.
                log.error("Could not settle held payouts for owner {}", entry.getKey(), e);
            }
        }
    }

    /**
     * Pays the owner's held rows if their onboarding is complete, and otherwise expires the rows
     * past the hold. A row is paid whenever completion is seen while it is still held, so an
     * owner who finishes on the last day is paid even if this sweep only notices an hour later.
     */
    private void releaseOrExpire(String ownerBusinessId, List<BundlePayout> held,
            LocalDateTime expireCreatedBefore) throws StripeException {

        Optional<StripeAccount> account = stripeAccountRepository.findByBusinessId(ownerBusinessId);
        if (account.isPresent() && StripeOnboardingState.isComplete(account.get())) {
            releaseAll(held, account.get().getStripeAccountId());
            return;
        }

        // created_at is the moment invoice.paid was handled, never before the payment itself,
        // so measuring the 90 days from it errs in the owner's favour.
        List<BundlePayout> overdue = held.stream()
                .filter(payout -> payout.getCreatedAt().isBefore(expireCreatedBefore))
                .toList();
        if (overdue.isEmpty()) {
            return;
        }

        // The stored flags are only as fresh as the last account.updated webhook or status read.
        // Ask Stripe before forfeiting money an owner might in fact be owed.
        if (account.isPresent() && isCompleteOnStripe(account.get())) {
            releaseAll(held, account.get().getStripeAccountId());
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        for (BundlePayout payout : overdue) {
            log.warn("Held payout {} ({} for invoice {}, owner {}) expired: Connect onboarding was still "
                            + "incomplete {} days after the invoice was paid",
                    payout.getId(), payout.getAmount(), payout.getStripeInvoiceId(), ownerBusinessId,
                    holdMaxAge.toDays());
            payout.setStatus(BundlePayoutStatus.EXPIRED_NOT_ONBOARDED);
            payout.setHoldEndedAt(now);
            payoutRepository.save(payout);
        }
    }

    /** Reads the account from Stripe, stores any change, and says whether it is now complete. */
    private boolean isCompleteOnStripe(StripeAccount account) throws StripeException {
        Account remote = Account.retrieve(account.getStripeAccountId());
        if (StripeAccountSync.apply(remote, account)) {
            stripeAccountRepository.save(account);
        }
        return StripeOnboardingState.isComplete(account);
    }

    private void releaseAll(List<BundlePayout> held, String destination) {
        for (BundlePayout payout : held) {
            try {
                release(payout, destination);
            } catch (Exception e) {
                // The row is untouched and still HELD, so the next sweep tries it again.
                log.error("Release of held payout {} (invoice {}, owner {}) failed unexpectedly",
                        payout.getId(), payout.getStripeInvoiceId(), payout.getMediaOwnerBusinessId(), e);
            }
        }
    }

    /**
     * Transfers one held share, with the amount and funding source stored when the invoice was
     * paid (never recomputed) and the usual idempotency key. Like {@link #retry}, it first asks
     * Stripe for a transfer that already went through, in case an earlier release succeeded but
     * was never recorded and its key has since expired.
     *
     * <p>A rejection makes the row an ordinary {@link BundlePayoutStatus#FAILED} one. An
     * insufficient-funds rejection is then retried by {@link #retryInsufficientFundsPayouts},
     * which counts its age limit from the release, not from the invoice.
     */
    private void release(BundlePayout payout, String destination) throws StripeException {
        Optional<Transfer> existing = findExistingTransfer(payout, destination);
        LocalDateTime now = LocalDateTime.now();
        if (existing.isPresent()) {
            log.warn("Held payout {} for invoice {} already has transfer {} on Stripe — recording that "
                    + "instead of transferring again", payout.getId(), payout.getStripeInvoiceId(),
                    existing.get().getId());
            payout.setHoldEndedAt(now);
            markPaid(payout, existing.get().getId());
            return;
        }

        try {
            Transfer transfer = createTransfer(payout.getStripeInvoiceId(), payout.getSubscriptionId(),
                    payout.getMediaOwnerBusinessId(), destination, payout.getAmount(),
                    payout.getStripeSourceChargeId());
            payout.setHoldEndedAt(now);
            payout.setLastAttemptAt(now);
            markPaid(payout, transfer.getId());
            log.info("Released held payout {}: paid owner {} {} for invoice {}: transfer {}", payout.getId(),
                    payout.getMediaOwnerBusinessId(), payout.getAmount(), payout.getStripeInvoiceId(),
                    transfer.getId());
        } catch (StripeException e) {
            log.error("Release of held payout {} for invoice {} was rejected by Stripe ({}): {}{}", payout.getId(),
                    payout.getStripeInvoiceId(), e.getCode(), e.getMessage(),
                    INSUFFICIENT_FUNDS.equals(e.getCode()) ? " — will be retried once funds settle" : "", e);
            payout.setHoldEndedAt(now);
            payout.setLastAttemptAt(now);
            payout.setStatus(BundlePayoutStatus.FAILED);
            payout.setFailureCode(e.getCode());
            payout.setFailureReason(e.getMessage());
            payoutRepository.save(payout);
        }
    }

    /** A transfer to this destination, made since the payout row, for this invoice and owner. */
    private Optional<Transfer> findExistingTransfer(BundlePayout payout, String destination) throws StripeException {
        long since = payout.getCreatedAt().minus(RECONCILE_SKEW).atZone(ZoneId.systemDefault()).toEpochSecond();
        TransferListParams params = TransferListParams.builder()
                .setDestination(destination)
                .setCreated(TransferListParams.Created.builder().setGte(since).build())
                .setLimit(100L)
                .build();
        for (Transfer transfer : Transfer.list(params).autoPagingIterable()) {
            Map<String, String> metadata = transfer.getMetadata();
            if (metadata != null
                    && payout.getStripeInvoiceId().equals(metadata.get("stripeInvoiceId"))
                    && payout.getMediaOwnerBusinessId().equals(metadata.get("mediaOwnerBusinessId"))) {
                return Optional.of(transfer);
            }
        }
        return Optional.empty();
    }

    private void markPaid(BundlePayout payout, String transferId) {
        payout.setStatus(BundlePayoutStatus.PAID);
        payout.setStripeTransferId(transferId);
        payout.setFailureCode(null);
        payout.setFailureReason(null);
        payoutRepository.save(payout);
    }

    private Transfer createTransfer(String stripeInvoiceId, String subscriptionId, String ownerBusinessId,
            String destinationAccountId, BigDecimal netAmount, String sourceChargeId) throws StripeException {

        TransferCreateParams.Builder params = TransferCreateParams.builder()
                .setAmount(toCents(netAmount))
                .setCurrency(Currency.CAD.toString().toLowerCase())
                .setDestination(destinationAccountId)
                .putMetadata("stripeInvoiceId", stripeInvoiceId)
                .putMetadata("subscriptionId", subscriptionId)
                .putMetadata("mediaOwnerBusinessId", ownerBusinessId);
        if (sourceChargeId != null) {
            // Ties the transfer to the invoice's charge, so it succeeds while that charge is
            // still pending. Null means the D30 overpay case; see sourceChargeFor.
            params.setSourceTransaction(sourceChargeId);
        }

        // NOT redundant with the ledger — the two guards cover different failures and
        // neither is sufficient alone. The ledger row is invisible to a concurrent
        // transaction until commit, so two deliveries processed in parallel both see
        // exists() == false and both reach this call; only one loses on the unique
        // index, and by then the money has moved twice. This stable key is what makes
        // the second call return the FIRST transfer instead of creating another. It is
        // also the only guard if the transaction rolls back after a transfer succeeds.
        // Conversely Stripe retains keys only ~24h, well inside its ~3-day retry
        // schedule — which is the window the ledger covers. Do not remove either.
        // The retry job reuses this key too, so a retry must send identical parameters.
        RequestOptions requestOptions = RequestOptions.builder()
                .setIdempotencyKey("payout-" + stripeInvoiceId + "-" + ownerBusinessId)
                .build();

        return Transfer.create(params.build(), requestOptions);
    }

    private static long toCents(BigDecimal amount) {
        return amount.multiply(ONE_HUNDRED).longValueExact();
    }

    /**
     * Inserts the ledger row. {@code failure} and the attempt time describe a transfer attempt,
     * so a held or skipped owner has neither. A held owner keeps {@code sourceChargeId}, for the
     * transfer made on release.
     */
    private void record(String stripeInvoiceId, String subscriptionId, String ownerBusinessId,
            BigDecimal grossAmount, BigDecimal netAmount, BundlePayoutStatus status,
            String transferId, String sourceChargeId, StripeException failure) {

        BundlePayout payout = new BundlePayout();
        payout.setStripeInvoiceId(stripeInvoiceId);
        payout.setSubscriptionId(subscriptionId);
        payout.setMediaOwnerBusinessId(ownerBusinessId);
        payout.setGrossAmount(grossAmount.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        payout.setAmount(netAmount.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        payout.setStatus(status);
        payout.setStripeTransferId(transferId);
        if (status == BundlePayoutStatus.PAID || status == BundlePayoutStatus.FAILED) {
            payout.setStripeSourceChargeId(sourceChargeId);
            payout.setLastAttemptAt(LocalDateTime.now());
        } else if (status == BundlePayoutStatus.HELD_NOT_ONBOARDED) {
            payout.setStripeSourceChargeId(sourceChargeId);
        }
        if (failure != null) {
            payout.setFailureCode(failure.getCode());
            payout.setFailureReason(failure.getMessage());
        }
        payoutRepository.save(payout);
    }
}
