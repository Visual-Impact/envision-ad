import { axiosInstance } from "@/shared/api";
import { MediaLocation } from "@/entities/media-location";

const BASE_URL = "/media-locations";

export const getAllMediaLocations = async (businessId: string): Promise<MediaLocation[]> => {
    const response = await axiosInstance.get(`${BASE_URL}?businessId=${businessId}`);
    return response.data;
};
