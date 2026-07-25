export type BundleSubscriptionStatus = "INCOMPLETE" | "ACTIVE" | "PAST_DUE" | "CANCELED";

/**
 * Body of POST /bundle-subscriptions. Carries no price: the amount is recomputed
 * server-side from the pricing pipeline and a client-supplied figure is never trusted.
 */
export interface BundleSubscriptionRequestDTO {
    bundleId: string;
    campaignId: string;
    businessId: string;
}

/** Embedded-checkout handoff returned when a subscribe attempt opens a Stripe session. */
export interface BundleSubscriptionCheckout {
    clientSecret: string;
    sessionId: string;
    /** The local subscription row's public id, for correlation. */
    subscriptionId: string;
}
