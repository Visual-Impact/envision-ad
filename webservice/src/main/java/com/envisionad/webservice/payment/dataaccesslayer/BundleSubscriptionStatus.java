package com.envisionad.webservice.payment.dataaccesslayer;

/**
 * The subset of Stripe subscription statuses this platform acts on.
 *
 * <p>INCOMPLETE, ACTIVE and PAST_DUE are the "live" states covered by the partial
 * unique index {@code uq_bundle_subscriptions_active_per_business} — a business may
 * hold at most one subscription in those states per bundle. CANCELED is exempt, so a
 * business can resubscribe to a bundle it previously left.
 */
public enum BundleSubscriptionStatus {
    INCOMPLETE,
    ACTIVE,
    PAST_DUE,
    CANCELED
}
