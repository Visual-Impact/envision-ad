import { Venue, VenueRequestDTO } from "@/entities/venue";
import { axiosInstance } from "@/shared/api";

export const createVenue = async (data: VenueRequestDTO): Promise<Venue> => {
    const response = await axiosInstance.post("/venues", data);
    return response.data;
};
