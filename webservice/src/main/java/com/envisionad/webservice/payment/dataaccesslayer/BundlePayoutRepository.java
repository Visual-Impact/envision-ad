package com.envisionad.webservice.payment.dataaccesslayer;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
