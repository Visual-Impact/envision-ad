import {VerificationResponseDTO} from "@/entities/organization";
import { axiosInstance } from "@/shared/api";

export const denyOrganizationVerification = async (
    businessId: string,
    verificationId: string,
    reason: string
): Promise<VerificationResponseDTO> => {
    const response = await axiosInstance.patch(
        `/businesses/${businessId}/verifications/${verificationId}/deny`,
        reason,
        {
            headers: {
                'Content-Type': 'text/plain'
            }
        }
    );
    return response.data;
};