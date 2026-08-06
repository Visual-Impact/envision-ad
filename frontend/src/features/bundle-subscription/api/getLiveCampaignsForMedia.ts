import { LiveCampaign } from "@/entities/bundle-subscription";
import axiosInstance from "@/shared/api/axios/axios";

/**
 * The campaigns currently running on one screen, for the media owner's proof-of-display picker.
 *
 * Replaces the old approach of fetching that screen's reservations and deriving the distinct
 * campaign list client-side: filtering to live subscriptions now happens server-side, so the
 * picker cannot offer a campaign the proof endpoint would reject.
 */
export const getLiveCampaignsForMedia = async (mediaId: string): Promise<LiveCampaign[]> => {
    const response = await axiosInstance.get<LiveCampaign[]>("/bundle-subscriptions/live-campaigns", {
        params: { mediaId },
    });
    return response.data;
};
