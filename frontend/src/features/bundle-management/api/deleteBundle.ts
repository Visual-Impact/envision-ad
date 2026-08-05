import axiosInstance from "@/shared/api/axios/axios";

export const deleteBundle = async (bundleId: string): Promise<void> => {
    await axiosInstance.delete(`/bundles/${bundleId}`);
};
