import { MediaRequestDTO, Media } from "@/entities/media"
import { axiosInstance } from "@/shared/api";

export async function addMedia(media: Omit<MediaRequestDTO, 'id'>): Promise<Media> {
    const response = await axiosInstance.post(`/media`, media);
    return response.data;
}