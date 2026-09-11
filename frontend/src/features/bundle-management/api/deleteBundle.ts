import { axiosInstance } from "@/shared/api";

export const deleteBundle = async (bundleId: string): Promise<void> => {
    await axiosInstance.delete(`/bundles/${bundleId}`);
};
