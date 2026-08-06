package com.envisionad.webservice.payment.dataaccesslayer;

/**
 * Outcome of one media owner's payout for one invoice.
 *
 * <p>All three are terminal for that invoice: a row exists, so the owner is never
 * paid twice, and never retried either. That matches the brief's stated intent —
 * an owner whose Connect onboarding has lapsed simply misses that cycle, with no
 * compensating job in scope. {@link #SKIPPED_NOT_ONBOARDED} and {@link #FAILED}
 * rows exist so a human can see who was missed and settle up out of band.
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
    /** Stripe rejected the transfer; {@code failureReason} carries its message. */
    FAILED
}
