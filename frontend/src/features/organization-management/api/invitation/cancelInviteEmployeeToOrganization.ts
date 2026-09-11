import { axiosInstance } from "@/shared/api";

export const cancelInviteEmployeeToOrganization = async (
    organizationId: string,
    invitationId: string
): Promise<void> => {
    const response = await axiosInstance.delete(`businesses/${organizationId}/invites/${invitationId}`);
    return response.data;
};