import axiosInstance from "@/shared/api/axios/axios";

export const addExclusion = async (bundleId: string, mediaId: string): Promise<void> => {
    await axiosInstance.put(`/bundles/${bundleId}/excluded-medias/${mediaId}`);
};
