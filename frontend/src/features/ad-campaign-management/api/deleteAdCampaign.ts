import { axiosInstance } from "@/shared/api";

export const deleteAdCampaign = async(businessId: string, campaignId: string): Promise<void> => {
    const response = await axiosInstance.delete(`/businesses/${businessId}/campaigns/${campaignId}`);
    return response.data;
}