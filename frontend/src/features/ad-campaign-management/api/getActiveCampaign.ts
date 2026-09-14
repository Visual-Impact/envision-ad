import { ActiveCampaignSummary } from "@/entities/ad-campaign";
import { axiosInstance } from "@/shared/api";

/** The campaign currently on screen, or null when none is set — the API answers 204, not 404. */
export const getActiveCampaign = async (businessId: string): Promise<ActiveCampaignSummary | null> => {
    const response = await axiosInstance.get<ActiveCampaignSummary>(`/businesses/${businessId}/active-campaign`);
    return response.status === 204 ? null : response.data;
};
