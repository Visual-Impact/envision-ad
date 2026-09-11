import { Media } from "@/entities/media";
import { axiosInstance } from "@/shared/api";


export async function getMediaByBusinessId(businessId: string): Promise<Media[]> {
    const response = await axiosInstance.get(`/businesses/${businessId}/media`);
    return response.data;
}
