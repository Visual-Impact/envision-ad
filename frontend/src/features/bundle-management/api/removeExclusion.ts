import axiosInstance from "@/shared/api/axios/axios";

export const removeExclusion = async (bundleId: string, mediaId: string): Promise<void> => {
    await axiosInstance.delete(`/bundles/${bundleId}/excluded-medias/${mediaId}`);
};
