import {AdCampaign} from "@/entities/ad-campaign";
import { axiosInstance } from "@/shared/api";

export const getAllAdCampaigns = async (
    businessId: string
): Promise<AdCampaign[]> => {
    const response = await axiosInstance.get<AdCampaign[]>(`/businesses/${businessId}/campaigns`);

    return response.data;
};