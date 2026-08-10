import { Media } from "@/entities/media"
import axiosInstance from "@/shared/api/axios/axios";

export async function getPendingMedia(): Promise<Media[]> {
    const response = await axiosInstance.get(`/media/pending`);
    return response.data;
}
