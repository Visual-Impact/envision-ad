import { RoleRemovalEligibilityDTO } from "@/entities/account";
import axiosInstance from "@/shared/api/axios/axios";

export const getRoleRemovalEligibility = async (businessId: string): Promise<RoleRemovalEligibilityDTO> => {
    const response = await axiosInstance.get(`/admin/accounts/${businessId}/roles/removal-eligibility`);
    return response.data;
};
