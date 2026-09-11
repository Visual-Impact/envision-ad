import {Ad, AdRequestDTO} from "@/entities/ad";
import { axiosInstance } from "@/shared/api";

export const addAdToCampaign = async (
    businessId: string,
    campaignId: string,
    data: AdRequestDTO
): Promise<Ad> => {
    const response = await axiosInstance.post(`/businesses/${businessId}/campaigns/${campaignId}/ads`, data);
    return response.data;
};