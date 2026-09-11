import { axiosInstance } from "@/shared/api";
import { MediaLocation } from "@/entities/media-location";

const BASE_URL = "/media-locations";

export const getMediaLocationById = async (id: string): Promise<MediaLocation> => {
    const response = await axiosInstance.get(`${BASE_URL}/${id}`);
    return response.data;
};
