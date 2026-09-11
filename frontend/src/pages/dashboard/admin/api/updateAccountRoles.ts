import { UpdateRolesResponseDTO } from "../model/account";
import { Roles } from "@/entities/organization";
import { axiosInstance } from "@/shared/api";

export const updateAccountRoles = async (businessId: string, roles: Roles): Promise<UpdateRolesResponseDTO> => {
    const response = await axiosInstance.patch(`/admin/accounts/${businessId}/roles`, roles);
    return response.data;
};
