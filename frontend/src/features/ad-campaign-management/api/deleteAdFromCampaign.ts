import { axiosInstance } from "@/shared/api";

export const deleteAdFromCampaign = async (
    businessId: string,
    campaignId: string,
    adId: string
): Promise<void> => {
    const response = await axiosInstance.delete(
        `/businesses/${businessId}/campaigns/${campaignId}/ads/${adId}`
    );
    return response.data;
};
