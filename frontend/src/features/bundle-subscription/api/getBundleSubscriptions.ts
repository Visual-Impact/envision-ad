import { BundleSubscription } from "@/entities/bundle-subscription";
import { axiosInstance } from "@/shared/api";

/**
 * Every bundle subscription held by a business, newest first — including INCOMPLETE and
 * CANCELED rows, which the advertiser needs to see (an abandoned checkout occupies their
 * one-live-subscription slot for that bundle until it is cancelled).
 */
export const getBundleSubscriptions = async (
    businessId: string,
): Promise<BundleSubscription[]> => {
    const response = await axiosInstance.get<BundleSubscription[]>("/bundle-subscriptions", {
        params: { businessId },
    });
    return response.data;
};
