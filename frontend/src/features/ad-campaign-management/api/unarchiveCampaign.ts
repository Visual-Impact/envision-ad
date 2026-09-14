import { axiosInstance } from "@/shared/api";

export const unarchiveCampaign = async (businessId: string, campaignId: string): Promise<void> => {
    await axiosInstance.post(`/businesses/${businessId}/campaigns/${campaignId}/unarchive`);
};
