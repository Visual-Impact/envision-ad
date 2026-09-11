import { Venue, VenueRequestDTO } from "@/entities/venue";
import { axiosInstance } from "@/shared/api";

export const updateVenue = async (venueId: string, data: VenueRequestDTO): Promise<Venue> => {
    const response = await axiosInstance.put(`/venues/${venueId}`, data);
    return response.data;
};
