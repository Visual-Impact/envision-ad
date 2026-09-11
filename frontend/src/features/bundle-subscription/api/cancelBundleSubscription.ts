import { axiosInstance } from "@/shared/api";

/**
 * Cancels at the end of the current billing period — never immediately. The advertiser keeps
 * the bundle's screens until `currentPeriodEnd`, so the subscription stays ACTIVE with
 * `cancelAtPeriodEnd` set rather than flipping to CANCELED straight away.
 *
 * Idempotent: cancelling twice returns 204 both times.
 */
export const cancelBundleSubscription = async (subscriptionId: string): Promise<void> => {
    await axiosInstance.post(`/bundle-subscriptions/${subscriptionId}/cancel`);
};
