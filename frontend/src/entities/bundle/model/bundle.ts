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
    /** Null when ruleType is FULL_NETWORK. */
    ruleValue?: string | null;
    active: boolean;
    /** Eligible screens after exclusions. */
    screenCount: number;
    /** Sum of the eligible screens' monthly prices. */
    basePrice: number;
    /** Subscriptions in INCOMPLETE/ACTIVE/PAST_DUE — non-zero blocks deletion. */
    activeSubscriptionCount: number;
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
