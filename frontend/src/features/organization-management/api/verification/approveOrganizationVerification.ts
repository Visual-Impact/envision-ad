import {VerificationResponseDTO} from "@/entities/organization";
import { axiosInstance } from "@/shared/api";

export const approveOrganizationVerification = async (
    businessId: string,
    verificationId: string
): Promise<VerificationResponseDTO> => {
    const response = await axiosInstance.patch(
        `/businesses/${businessId}/verifications/${verificationId}/approve`
    );
    return response.data;
};