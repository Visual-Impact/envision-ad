import { InvitationAcceptResponse } from "@/entities/organization";
import { axiosInstance } from "@/shared/api";

// P5 FR 3.2: callable regardless of session state now — axiosInstance simply omits the
// Authorization header when there's no session, and the backend branches on that (see
// BusinessServiceImpl.addBusinessEmployee) instead of requiring auth up front.
export const addEmployeeToOrganization = async (
    organizationId: string,
    token: string
): Promise<InvitationAcceptResponse> => {
    const response = await axiosInstance.post(`/businesses/${organizationId}/employees?token=${token}`);
    return response.data;
}
