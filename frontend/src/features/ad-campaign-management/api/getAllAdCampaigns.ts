import {AdCampaign} from "@/entities/ad-campaign";
import { axiosInstance } from "@/shared/api";

/** Archived campaigns are left out unless asked for, matching the backend's default. */
export const getAllAdCampaigns = async (
    businessId: string,
    includeArchived = false
): Promise<AdCampaign[]> => {
    const response = await axiosInstance.get<AdCampaign[]>(`/businesses/${businessId}/campaigns`, {
        params: { includeArchived },
    });

    return response.data;
};
