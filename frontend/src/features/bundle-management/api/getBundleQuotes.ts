import { BundlePriceQuote } from "@/entities/bundle";
import { axiosInstance } from "@/shared/api";

/**
 * The buyer's quote for every active bundle in one call, so bundle cards can show what this
 * advertiser would actually pay (P4: screens in their own business type are left out).
 * Authenticated; the backend validates the caller is an employee of `businessId`.
 */
export const getBundleQuotes = async (businessId: string): Promise<BundlePriceQuote[]> => {
    const response = await axiosInstance.get("/bundles/quotes", { params: { businessId } });
    return response.data;
};
