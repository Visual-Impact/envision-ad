import { AccountBusinessResponseDTO } from "@/entities/account";
import axiosInstance from "@/shared/api/axios/axios";

export const setAccountActive = async (businessId: string, active: boolean): Promise<AccountBusinessResponseDTO> => {
    const response = await axiosInstance.patch(`/admin/accounts/${businessId}/active`, { active });
    return response.data;
};
