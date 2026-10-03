import { axiosInstance } from "@/shared/api";

export interface BusinessTypeChangeImpact {
    /** Screens of that venue the advertiser already pays for through live subscriptions. */
    liveSubscriptionScreenCount: number;
}

// Read-only preview for the admin business-type modal: setting a type never changes existing
// subscriptions, but their creatives stop being sent to these screens.
export const getBusinessTypeChangeImpact = async (
    businessId: string,
    businessTypeVenueId: string,
): Promise<BusinessTypeChangeImpact> => {
    const response = await axiosInstance.get(`/admin/accounts/${businessId}/business-type/impact`, {
        params: { businessTypeVenueId },
    });
    return response.data;
};
