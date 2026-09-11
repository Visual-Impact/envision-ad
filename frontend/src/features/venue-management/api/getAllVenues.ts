import { Venue } from "@/entities/venue";
import { axiosInstance } from "@/shared/api";

export const getAllVenues = async (locale: string = "en"): Promise<Venue[]> => {
    const response = await axiosInstance.get("/venues", { params: { locale } });
    return response.data;
};
