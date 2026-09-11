import { axiosInstance } from "@/shared/api";

export async function deleteMedia(id: string): Promise<void> {
    const response = await axiosInstance.delete(`/media/${id}`);
    return response.data;
}