package com.envisionad.webservice.payment.dataaccesslayer;

/**
 * Outcome of one media owner's payout for one invoice.
 *
 * <p>A row exists for every owner the webhook has handled, so it never pays the owner
 * twice. Most statuses are terminal; {@link #FAILED} rows exist so a human can see who
 * was missed and settle up out of band. Two are not:
 * <ul>
 *   <li>{@link #HELD_NOT_ONBOARDED}: released to {@link #PAID} (or {@link #FAILED}) once the
 *       owner's Connect onboarding completes, or moved to {@link #EXPIRED_NOT_ONBOARDED} after
 *       90 days (Terms of Service §7).</li>
 *   <li>A {@link #FAILED} row whose {@code failureCode} is {@code balance_insufficient}:
 *       {@code BundlePayoutRetryJob} retries it with the same idempotency key and parameters,
 *       and flips it to {@link #PAID} once funds have settled.</li>
 * </ul>
 */
public enum BundlePayoutStatus {
    /** Transfer created; {@code stripeTransferId} is set. */
    PAID,
    /**
     * Legacy, no longer written: the owner had no completed Connect onboarding and their
     * share was skipped for good. Rows from before the onboarding hold keep this status and
     * are settled by hand.
     */
    SKIPPED_NOT_ONBOARDED,
    /**
     * The owner had no {@code stripe_accounts} row, or had not completed onboarding, when the
     * invoice was paid. The share is owed and waits for onboarding to complete; the row keeps
     * the amount and funding source decided at payment time, so the release sends exactly those.
     */
    HELD_NOT_ONBOARDED,
    /**
     * A {@link #HELD_NOT_ONBOARDED} row whose owner still had not completed onboarding 90 days
     * after the invoice was paid. Terminal: under Terms of Service §7 the amount is no longer owed.
     */
    EXPIRED_NOT_ONBOARDED,
    /**
     * The owner's share rounded to zero, so there was nothing to transfer — Stripe
     * rejects zero-amount transfers. Reachable because {@code media.price} is
     * nullable and coalesces to zero (decision D13).
     */
    SKIPPED_ZERO_AMOUNT,
    /**
     * Stripe rejected the transfer; {@code failureReason} carries its message and
     * {@code failureCode} its error code. Retried only for {@code balance_insufficient}.
     */
    FAILED
}
