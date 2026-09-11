import {AdCampaign} from "@/entities/ad-campaign";
import { axiosInstance } from "@/shared/api";

export const getAdCampaignById = async (
    campaignId: string
): Promise<AdCampaign> => {
    const response = await axiosInstance.get<AdCampaign>(`/campaigns/${campaignId}`);

    return response.data;
};