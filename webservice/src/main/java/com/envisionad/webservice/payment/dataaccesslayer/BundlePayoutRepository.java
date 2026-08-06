package com.envisionad.webservice.payment.dataaccesslayer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface BundlePayoutRepository extends JpaRepository<BundlePayout, Long> {

    /**
     * The idempotency guard. Any row — PAID, SKIPPED or FAILED — means this owner
     * has already been handled for this invoice and must not be transferred to
     * again.
     */
    boolean existsByStripeInvoiceIdAndMediaOwnerBusinessId(
            String stripeInvoiceId, String mediaOwnerBusinessId);

    /** Everything paid out for one invoice; the natural unit for verifying a cycle. */
    List<BundlePayout> findAllByStripeInvoiceId(String stripeInvoiceId);

    /** The payout history of one subscription, across all its billing cycles. */
    List<BundlePayout> findAllBySubscriptionId(String subscriptionId);

    /**
     * A media owner's earnings for a reporting period (M6). This ledger is the dashboard's
     * earnings source because it records what was actually transferred: {@code amount} is the
     * post-fee figure as it stood at payout time, so a later change to
     * {@code stripe.platform-fee-percent} cannot retroactively rewrite past earnings.
     * <p>
     * Includes SKIPPED and FAILED rows — they carry a real {@code gross_amount} the owner earned
     * but was not paid, which is exactly the discrepancy a media owner needs to be able to see.
     */
    List<BundlePayout> findAllByMediaOwnerBusinessIdAndCreatedAtBetween(
            String mediaOwnerBusinessId, LocalDateTime start, LocalDateTime end);
}
