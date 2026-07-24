import { Bundle, BundleRuleType } from "@/entities/bundle";
import axiosInstance from "@/shared/api/axios/axios";

export const getAllBundles = async (
    ruleType?: BundleRuleType,
    active: boolean = true,
): Promise<Bundle[]> => {
    const response = await axiosInstance.get("/bundles", { params: { ruleType, active } });
    return response.data;
};
