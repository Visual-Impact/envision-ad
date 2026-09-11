import { axiosInstance } from "@/shared/api";

export const getVenueAdCount = async (venueId: string): Promise<number> => {
    const response = await axiosInstance.get<{ adCount: number }>(`/venues/${venueId}/ad-count`);

    return response.data.adCount;
};
