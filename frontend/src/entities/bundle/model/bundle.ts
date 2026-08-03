export type BundleRuleType = "CITY" | "REGION" | "VENUE" | "FULL_NETWORK";

export interface Bundle {
    bundleId: string;
    nameEn: string;
    nameFr: string;
    descriptionEn?: string;
    descriptionFr?: string;
    idealForEn?: string;
    idealForFr?: string;
    badgeColor: string;
    ruleType: BundleRuleType;
    /** Null when ruleType is FULL_NETWORK. For VENUE this is the venue's id, not its name. */
    ruleValue?: string | null;
    /**
     * ruleValue as a human reads it — the venue's name for VENUE, the raw city/region
     * string otherwise, null for FULL_NETWORK. Read-only: writes still send ruleValue.
     * Falls back to the raw id server-side when a VENUE rule points at a deleted venue.
     */
    ruleValueLabelEn?: string | null;
    ruleValueLabelFr?: string | null;
    active: boolean;
    /** Eligible screens after exclusions. */
    screenCount: number;
    /** Sum of the eligible screens' monthly prices, before any discount. */
    basePrice: number;
    /** What the advertiser pays: basePrice after the discount. Equals basePrice when discountPercent is 0. */
    finalPrice: number;
    /** Whole-percent discount on this bundle; 0 when none. */
    discountPercent: number;
    /**
     * Shared per-screen price when every eligible screen has the same non-null price;
     * null/absent for an empty, mixed, or partially-priceless set. Drives the card's
     * "$X × N screens" vs. "N screens" subline.
     */
    perScreenPrice?: number | null;
    /** perScreenPrice after the discount; null when there's no discount or no honest per-screen figure. */
    discountedPerScreenPrice?: number | null;
    /** Subscriptions in INCOMPLETE/ACTIVE/PAST_DUE — non-zero blocks deletion. */
    activeSubscriptionCount: number;
}

/** Buyer-specific price preview from GET /bundles/{id}/quote. */
export interface BundlePriceQuote {
    screenCount: number;
    finalPrice: number;
    perScreenPrice?: number | null;
}

export interface BundleRequestDTO {
    nameEn: string;
    nameFr: string;
    descriptionEn?: string;
    descriptionFr?: string;
    idealForEn?: string;
    idealForFr?: string;
    badgeColor: string;
    ruleType: BundleRuleType;
    ruleValue?: string | null;
    active?: boolean;
    /** Whole-percent discount, 0–100. Omitted or 0 means no discount. */
    discountPercent?: number;
}

/** One row of a bundle's rule-matched set, as shown in the exclusions modal. */
export interface BundleCandidateMedia {
    mediaId: string;
    title: string;
    mediaOwnerName: string;
    city?: string;
    region?: string;
    venueId?: string;
    price?: number;
    excluded: boolean;
}
