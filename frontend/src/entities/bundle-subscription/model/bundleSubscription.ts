export type BundleSubscriptionStatus = "INCOMPLETE" | "ACTIVE" | "PAST_DUE" | "CANCELED";

/**
 * Body of POST /bundle-subscriptions. Carries no price: the amount is recomputed
 * server-side from the pricing pipeline and a client-supplied figure is never trusted.
 */
export interface BundleSubscriptionRequestDTO {
    bundleId: string;
    campaignId: string;
    businessId: string;
    /** Optional Stripe coupon code, already previewed via /coupons/validate before
     * confirming checkout (P3). Omitted entirely when no coupon is applied. */
    couponCode?: string;
}

/** Embedded-checkout handoff returned when a subscribe attempt opens a Stripe session. */
export interface BundleSubscriptionCheckout {
    clientSecret: string;
    sessionId: string;
    /** The local subscription row's public id, for correlation. */
    subscriptionId: string;
}

/** One row of the advertiser's subscription list. */
export interface BundleSubscription {
    subscriptionId: string;
    bundleId: string;
    /** Null when the bundle was deleted after this subscription was cancelled. */
    bundleNameEn: string | null;
    bundleNameFr: string | null;

    status: BundleSubscriptionStatus;

    /** Locked at signup and never recomputed, even if the bundle's price changes later. */
    monthlyAmount: number;
    /** Screen count frozen at signup; the bundle may contain more by now. */
    screenCount: number;

    /**
     * Next renewal date, or null.
     *
     * Null is legitimate on an ACTIVE subscription rather than a data fault: activation happens
     * on `checkout.session.completed`, but only `invoice.paid` sets a renewal date. Render the
     * absence — never assume this is populated.
     */
    currentPeriodEnd: string | null;

    /**
     * True when the advertiser has cancelled but the paid-for period has not elapsed. Such a row
     * is still ACTIVE, so this is what separates "renews on X" from "ends on X".
     */
    cancelAtPeriodEnd: boolean;

    canceledAt: string | null;
    createdAt: string | null;

    campaignId: string;
    campaignName: string | null;
}

/** A campaign running on a given screen, for the proof-of-display picker. */
export interface LiveCampaign {
    campaignId: string;
    campaignName: string;
}
