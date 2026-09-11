// 'use server';
import { InvitationResponse } from "@/entities/organization";
import { axiosInstance } from "@/shared/api";

export const getAllOrganizationInvitations = async (
    organizationId: string
): Promise<InvitationResponse[]> => {
    const response = await axiosInstance.get(`/businesses/${organizationId}/invites`);
    return response.data;
};