import { RoleRemovalEligibilityDTO } from "../model/account";
import { axiosInstance } from "@/shared/api";

export const getRoleRemovalEligibility = async (businessId: string): Promise<RoleRemovalEligibilityDTO> => {
    const response = await axiosInstance.get(`/admin/accounts/${businessId}/roles/removal-eligibility`);
    return response.data;
};
