import type { Ad } from '@/entities/ad/@x/ad-campaign';



export interface AdCampaign {
    campaignId: string;
    name: string;
    startDate: string; // or Date
    endDate: string;   // or Date
    ads: Ad[];
    /** Set once the advertiser archives the campaign; null while it is in their list. */
    archivedAt: string | null;
}

export interface AdCampaignRequestDTO {
    name: string;
}

/**
 * The business's "Currently Displaying" campaign, as the dashboard slot shows it.
 * Timestamps are ISO strings with an offset, or null when the event never happened.
 */
export interface ActiveCampaignSummary {
    campaignId: string;
    name: string;
    ads: Ad[];
    subscribedBundleCount: number;
    subscribedScreenCount: number;
    /** The last swap or manual notify — the person-triggered sends that start the cooldown. */
    lastSwapAt: string | null;
    /** Set only while the swap/notify cooldown is in force. */
    swapAvailableAt: string | null;
    /** The last time the automatic sweep emailed media owners on the advertiser's behalf. */
    lastAutoNotifiedAt: string | null;
    /** Creatives changed since media owners were last told. */
    hasUnnotifiedCreativeChanges: boolean;
}

/** How many media owners a notify actually reached. */
export interface NotificationResult {
    notifiedCount: number;
    failedCount: number;
}