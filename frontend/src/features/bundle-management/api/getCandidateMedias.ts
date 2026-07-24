import { BundleCandidateMedia } from "@/entities/bundle";
import axiosInstance from "@/shared/api/axios/axios";

export const getCandidateMedias = async (bundleId: string): Promise<BundleCandidateMedia[]> => {
    const response = await axiosInstance.get(`/bundles/${bundleId}/candidate-medias`);
    return response.data;
};
