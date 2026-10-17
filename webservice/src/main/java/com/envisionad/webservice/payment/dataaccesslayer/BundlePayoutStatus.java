package com.envisionad.webservice.payment.dataaccesslayer;

/**
 * Outcome of one media owner's payout for one invoice.
 *
 * <p>Terminal for that invoice, with one exception: a row exists, so the webhook never
 * pays the owner twice and never retries. An owner whose Connect onboarding has lapsed
 * simply misses that cycle. {@link #SKIPPED_NOT_ONBOARDED} and {@link #FAILED} rows exist
 * so a human can see who was missed and settle up out of band.
 *
 * <p>The exception is a {@link #FAILED} row whose {@code failureCode} is
 * {@code balance_insufficient}: {@code BundlePayoutRetryJob} retries it with the same
 * idempotency key and parameters, and flips it to {@link #PAID} once funds have settled.
 */
public enum BundlePayoutStatus {
    /** Transfer created; {@code stripeTransferId} is set. */
    PAID,
    /** Owner has no {@code stripe_accounts} row, or has not completed onboarding. */
    SKIPPED_NOT_ONBOARDED,
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
