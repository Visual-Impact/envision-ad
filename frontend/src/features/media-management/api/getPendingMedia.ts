import { Media } from "@/entities/media"
import { axiosInstance } from "@/shared/api";

export async function getPendingMedia(): Promise<Media[]> {
    const response = await axiosInstance.get(`/media/pending`);
    return response.data;
}
