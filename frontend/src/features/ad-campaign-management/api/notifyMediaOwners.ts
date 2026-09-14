import { NotificationResult } from "@/entities/ad-campaign";
import { axiosInstance } from "@/shared/api";

/** Re-sends the active campaign's current creatives to the affected media owners. */
export const notifyMediaOwners = async (businessId: string): Promise<NotificationResult> => {
    const response = await axiosInstance.post<NotificationResult>(`/businesses/${businessId}/active-campaign/notify`);
    return response.data;
};
