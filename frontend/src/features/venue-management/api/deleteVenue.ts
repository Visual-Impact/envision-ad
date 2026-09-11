import { axiosInstance } from "@/shared/api";

export const deleteVenue = async (venueId: string): Promise<void> => {
    await axiosInstance.delete(`/venues/${venueId}`);
};
