package com.envisionad.webservice.payment.businesslogiclayer;

import com.envisionad.webservice.payment.dataaccesslayer.*;
import com.stripe.exception.StripeException;
import com.stripe.model.Transfer;
import com.stripe.net.RequestOptions;
import com.stripe.param.TransferCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    @Value("${stripe.platform-fee-percent}")
    private int platformFeePercent;

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

        log.info("Paying out invoice {} for subscription {}: {} screens across {} owner(s)",
                stripeInvoiceId, subscriptionId, items.size(), sharesByOwner.size());

        for (Map.Entry<String, BigDecimal> share : sharesByOwner.entrySet()) {
            payOutOwner(stripeInvoiceId, subscriptionId, share.getKey(), share.getValue());
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
     * That is why transfers are made from the platform balance rather than against
     * the invoice's charge — Stripe caps {@code source_transaction} transfers at the
     * source charge's amount, which would make exactly that case fail.
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

    /**
     * One owner's transfer. Every failure mode is contained here: an owner who
     * cannot be paid is recorded and logged, and the loop continues to the next.
     */
    private void payOutOwner(String stripeInvoiceId, String subscriptionId,
            String ownerBusinessId, BigDecimal grossAmount) {

        // The idempotency guard. Any existing row — paid, skipped or failed — means
        // this owner has already been handled for this invoice. Redelivery is routine
        // here: invoice.paid throws on lookup miss specifically to make Stripe retry.
        if (payoutRepository.existsByStripeInvoiceIdAndMediaOwnerBusinessId(stripeInvoiceId, ownerBusinessId)) {
            log.info("Owner {} already handled for invoice {} — skipping (redelivered event)",
                    ownerBusinessId, stripeInvoiceId);
            return;
        }

        BigDecimal netAmount = grossAmount
                .multiply(BigDecimal.valueOf(100L - platformFeePercent))
                .divide(ONE_HUNDRED, MONEY_SCALE, RoundingMode.HALF_UP);

        if (netAmount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Owner {} share on invoice {} rounds to zero (gross {}) — nothing to transfer",
                    ownerBusinessId, stripeInvoiceId, grossAmount);
            record(stripeInvoiceId, subscriptionId, ownerBusinessId, grossAmount, netAmount,
                    null, BundlePayoutStatus.SKIPPED_ZERO_AMOUNT, null);
            return;
        }

        Optional<StripeAccount> account = stripeAccountRepository.findByBusinessId(ownerBusinessId);
        if (account.isEmpty() || !account.get().isOnboardingComplete()) {
            log.warn("Owner {} cannot be paid for invoice {} ({}): skipping their {} share, "
                            + "other owners on this invoice are unaffected",
                    ownerBusinessId, stripeInvoiceId,
                    account.isEmpty() ? "no Stripe account" : "Connect onboarding incomplete", netAmount);
            record(stripeInvoiceId, subscriptionId, ownerBusinessId, grossAmount, netAmount,
                    null, BundlePayoutStatus.SKIPPED_NOT_ONBOARDED, null);
            return;
        }

        try {
            Transfer transfer = createTransfer(stripeInvoiceId, subscriptionId, ownerBusinessId,
                    account.get().getStripeAccountId(), netAmount);
            record(stripeInvoiceId, subscriptionId, ownerBusinessId, grossAmount, netAmount,
                    transfer.getId(), BundlePayoutStatus.PAID, null);
            log.info("Paid owner {} {} for invoice {} (gross {}, {}% platform fee): transfer {}",
                    ownerBusinessId, netAmount, stripeInvoiceId, grossAmount, platformFeePercent, transfer.getId());
        } catch (StripeException e) {
            // Swallowed on purpose. Rethrowing would fail the webhook, and Stripe would
            // redeliver an event whose other transfers have already moved money.
            log.error("Transfer to owner {} for invoice {} was rejected by Stripe: {}",
                    ownerBusinessId, stripeInvoiceId, e.getMessage(), e);
            record(stripeInvoiceId, subscriptionId, ownerBusinessId, grossAmount, netAmount,
                    null, BundlePayoutStatus.FAILED, e.getMessage());
        }
    }

    private Transfer createTransfer(String stripeInvoiceId, String subscriptionId,
            String ownerBusinessId, String destinationAccountId, BigDecimal netAmount) throws StripeException {

        long amountInCents = netAmount.multiply(ONE_HUNDRED).longValueExact();

        TransferCreateParams params = TransferCreateParams.builder()
                .setAmount(amountInCents)
                .setCurrency(Currency.CAD.toString().toLowerCase())
                .setDestination(destinationAccountId)
                // Deliberately no source_transaction. Stripe caps such transfers at the
                // source charge's amount, which would reject the accepted case where a
                // bundle discount exceeds the platform fee (D30). Paying from the
                // platform balance has no such ceiling.
                .putMetadata("stripeInvoiceId", stripeInvoiceId)
                .putMetadata("subscriptionId", subscriptionId)
                .putMetadata("mediaOwnerBusinessId", ownerBusinessId)
                .build();

        // NOT redundant with the ledger — the two guards cover different failures and
        // neither is sufficient alone. The ledger row is invisible to a concurrent
        // transaction until commit, so two deliveries processed in parallel both see
        // exists() == false and both reach this call; only one loses on the unique
        // index, and by then the money has moved twice. This stable key is what makes
        // the second call return the FIRST transfer instead of creating another. It is
        // also the only guard if the transaction rolls back after a transfer succeeds.
        // Conversely Stripe retains keys only ~24h, well inside its ~3-day retry
        // schedule — which is the window the ledger covers. Do not remove either.
        RequestOptions requestOptions = RequestOptions.builder()
                .setIdempotencyKey("payout-" + stripeInvoiceId + "-" + ownerBusinessId)
                .build();

        return Transfer.create(params, requestOptions);
    }

    private void record(String stripeInvoiceId, String subscriptionId, String ownerBusinessId,
            BigDecimal grossAmount, BigDecimal netAmount, String transferId,
            BundlePayoutStatus status, String failureReason) {

        BundlePayout payout = new BundlePayout();
        payout.setStripeInvoiceId(stripeInvoiceId);
        payout.setSubscriptionId(subscriptionId);
        payout.setMediaOwnerBusinessId(ownerBusinessId);
        payout.setGrossAmount(grossAmount.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        payout.setAmount(netAmount.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        payout.setStripeTransferId(transferId);
        payout.setStatus(status);
        payout.setFailureReason(failureReason);
        payoutRepository.save(payout);
    }
}
