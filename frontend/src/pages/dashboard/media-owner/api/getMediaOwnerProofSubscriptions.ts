import type { BundleSubscriptionStatus, ProofLink } from "@/entities/bundle-subscription";
import { axiosInstance } from "@/shared/api";

/**
 * One row of the media owner's Proof of Display table (P12): a subscription covering at least one
 * of their screens. Identified by advertiser + bundle; the campaign is whatever the advertiser has
 * on screen right now, and may be null.
 */
export interface MediaOwnerProofSubscription {
    subscriptionId: string;
    subscriptionStatus: BundleSubscriptionStatus;
    advertiserBusinessName: string | null;
    /** Null when the bundle was deleted after this subscription was cancelled. */
    bundleNameEn: string | null;
    bundleNameFr: string | null;
    campaignName: string | null;
    /** Only this owner's screens, even on a subscription that spans several owners. */
    screenNames: string[];
    proofLink: ProofLink;
    /** When the link was last saved; null unless `proofLink.status` is `SET`. */
    linkUpdatedAt: string | null;
}

export const getMediaOwnerProofSubscriptions = async (
    businessId: string,
): Promise<MediaOwnerProofSubscription[]> => {
    const response = await axiosInstance.get<MediaOwnerProofSubscription[]>(
        `/proof-of-display/media-owner/${businessId}/subscriptions`,
    );
    return response.data;
};
