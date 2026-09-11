import { Media, MediaRequestDTO } from "@/entities/media"
import { axiosInstance } from "@/shared/api";

export async function updateMedia(id: string, media: Partial<MediaRequestDTO>): Promise<Media> {
    const response = await axiosInstance.put(`/media/${id}`, media);
    return response.data;
}