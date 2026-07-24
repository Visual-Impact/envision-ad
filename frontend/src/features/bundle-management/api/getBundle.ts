import { Bundle } from "@/entities/bundle";
import axiosInstance from "@/shared/api/axios/axios";

export const getBundle = async (bundleId: string): Promise<Bundle> => {
    const response = await axiosInstance.get(`/bundles/${bundleId}`);
    return response.data;
};
