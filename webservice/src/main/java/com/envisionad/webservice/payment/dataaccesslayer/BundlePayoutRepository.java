package com.envisionad.webservice.payment.dataaccesslayer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface BundlePayoutRepository extends JpaRepository<BundlePayout, Long> {

    /**
     * The idempotency guard. Any row — PAID, HELD, SKIPPED or FAILED — means this owner
     * has already been handled for this invoice and must not be transferred to
     * again by the webhook. Retrying an insufficient-funds FAILED row is the retry
     * job's business, and releasing a HELD row the onboarding hold's, not the webhook's.
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

    /**
     * The retry job's work list: rows in {@code status} that failed with {@code failureCode},
     * were last attempted before {@code lastAttemptBefore}, and whose first attempt came after
     * {@code firstAttemptAfter} (older ones are left for a human). The first attempt is the
     * release for a row that was held for onboarding, and the row's creation otherwise.
     */
    @Query("""
            SELECT p FROM BundlePayout p
            WHERE p.status = :status
              AND p.failureCode = :failureCode
              AND p.lastAttemptAt < :lastAttemptBefore
              AND COALESCE(p.holdEndedAt, p.createdAt) > :firstAttemptAfter
            """)
    List<BundlePayout> findRetryable(
            @Param("status") BundlePayoutStatus status, @Param("failureCode") String failureCode,
            @Param("lastAttemptBefore") LocalDateTime lastAttemptBefore,
            @Param("firstAttemptAfter") LocalDateTime firstAttemptAfter);

    /** One owner's shares waiting on onboarding (status {@code HELD_NOT_ONBOARDED}), oldest first. */
    List<BundlePayout> findAllByMediaOwnerBusinessIdAndStatusOrderByCreatedAtAsc(
            String mediaOwnerBusinessId, BundlePayoutStatus status);

    /** Every row in {@code status}, oldest first: the hold sweep's work list. */
    List<BundlePayout> findAllByStatusOrderByCreatedAtAsc(BundlePayoutStatus status);
}
