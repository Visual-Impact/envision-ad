import { ActiveCampaignSummary } from "@/entities/ad-campaign";
import { axiosInstance } from "@/shared/api";

/** Puts another campaign on screen and emails the affected media owners. */
export const swapActiveCampaign = async (businessId: string, campaignId: string): Promise<ActiveCampaignSummary> => {
    const response = await axiosInstance.put<ActiveCampaignSummary>(
        `/businesses/${businessId}/active-campaign`,
        { campaignId },
    );
    return response.data;
};
