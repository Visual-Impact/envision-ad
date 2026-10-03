import { AccountBusinessResponseDTO } from "../model/account";
import { axiosInstance } from "@/shared/api";

// null clears the business type. Admin-only: the business type drives competitive exclusion (P4).
export const updateAccountBusinessType = async (
    businessId: string,
    businessTypeVenueId: string | null,
): Promise<AccountBusinessResponseDTO> => {
    const response = await axiosInstance.patch(`/admin/accounts/${businessId}/business-type`, { businessTypeVenueId });
    return response.data;
};
