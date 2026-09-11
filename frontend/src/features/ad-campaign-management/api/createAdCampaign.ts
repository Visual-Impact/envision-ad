import {AdCampaign, AdCampaignRequestDTO} from "@/entities/ad-campaign";
import { axiosInstance } from "@/shared/api";

export const createAdCampaign = async (
    businessId: string,
    data: AdCampaignRequestDTO
): Promise<AdCampaign> => {
    const response = await axiosInstance.post(`/businesses/${businessId}/campaigns`, data);
    return response.data;
};