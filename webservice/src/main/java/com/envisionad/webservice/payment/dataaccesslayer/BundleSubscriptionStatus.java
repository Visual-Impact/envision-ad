package com.envisionad.webservice.payment.dataaccesslayer;

import java.util.List;

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
    CANCELED;

    /**
     * A subscription that is currently being paid for, and therefore currently entitles
     * the advertiser to screen time and the media owner to revenue. This is the set every
     * "does this business have a live subscription?" question means, and it deliberately
     * excludes INCOMPLETE: a checkout that was started and never paid grants nothing.
     *
     * <p>Extracted here in P6 M2a because four services had each declared a private copy
     * (two as {@code List}, one as {@code Set}, under two different names), which made it
     * impossible to tell whether they were the same rule or four rules that happened to
     * agree. They were the same rule.
     *
     * <p>Not to be confused with the duplicate-subscription guard's status set, which
     * answers a different question (which states block a second subscription to the same
     * bundle) and keeps its own constant in {@code BundleSubscriptionServiceImpl}.
     */
    public static final List<BundleSubscriptionStatus> LIVE = List.of(ACTIVE, PAST_DUE);
}
