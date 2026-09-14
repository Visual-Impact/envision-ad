import { AdCampaign } from "@/entities/ad-campaign";
import { axiosInstance } from "@/shared/api";

/** The campaigns that could go on screen instead: at least one creative, and not the active one. */
export const getEligibleSwapCampaigns = async (businessId: string): Promise<AdCampaign[]> => {
    const response = await axiosInstance.get<AdCampaign[]>(`/businesses/${businessId}/campaigns/eligible-for-swap`);
    return response.data;
};
