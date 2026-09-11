import { Media } from "@/entities/media";
import { axiosInstance } from "@/shared/api";

export async function getMediaById(id: string): Promise<Media> {
    const response = await axiosInstance(`/media/${id}`);

    return response.data;
}