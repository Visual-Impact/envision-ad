import { BundlePriceQuote } from "@/entities/bundle";
import { axiosInstance } from "@/shared/api";

/**
 * Buyer-specific price preview for a bundle. Authenticated; the backend validates
 * the caller is an employee of `businessId`. First consumed by the M4 subscribe flow.
 */
export const getBundleQuote = async (
    bundleId: string,
    businessId: string,
): Promise<BundlePriceQuote> => {
    const response = await axiosInstance.get(`/bundles/${bundleId}/quote`, {
        params: { businessId },
    });
    return response.data;
};
