import { Ad } from "@/entities/ad";
import axiosInstance from "@/shared/api/axios/axios";

export const updateAdVenueTags = async (
    businessId: string,
    campaignId: string,
    adId: string,
    venueIds: string[]
): Promise<Ad> => {
    const response = await axiosInstance.put<Ad>(
        `/businesses/${businessId}/campaigns/${campaignId}/ads/${adId}/venue-tags`,
        { venueIds }
    );

    return response.data;
};
