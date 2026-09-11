import { InvitationRequest } from "@/entities/organization";
import { axiosInstance } from "@/shared/api";

export const createInviteEmployeeToOrganization = async (
    organizationId: string,
    invitation: InvitationRequest
): Promise<void> => {
    const response = await axiosInstance.post(`/businesses/${organizationId}/invites`, invitation);
    return response.data;
};


