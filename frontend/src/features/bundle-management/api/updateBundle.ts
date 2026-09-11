import { Bundle, BundleRequestDTO } from "@/entities/bundle";
import { axiosInstance } from "@/shared/api";

export const updateBundle = async (bundleId: string, data: BundleRequestDTO): Promise<Bundle> => {
    const response = await axiosInstance.put(`/bundles/${bundleId}`, data);
    return response.data;
};
