import { BundleSubscriptionCheckout, BundleSubscriptionRequestDTO } from "@/entities/bundle-subscription";
import { axiosInstance } from "@/shared/api";

/**
 * Opens a subscription-mode Stripe Checkout Session and freezes the locked price and
 * per-owner split. Returns the client secret the embedded checkout mounts.
 */
export const createBundleSubscription = async (
    payload: BundleSubscriptionRequestDTO,
): Promise<BundleSubscriptionCheckout> => {
    const response = await axiosInstance.post<BundleSubscriptionCheckout>(
        "/bundle-subscriptions",
        payload,
    );
    return response.data;
};
