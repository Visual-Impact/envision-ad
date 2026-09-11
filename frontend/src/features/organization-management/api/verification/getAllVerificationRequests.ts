import { axiosInstance } from "@/shared/api";
import {VerificationResponseDTO} from "@/entities/organization";

export const getAllVerificationRequests = async (): Promise<VerificationResponseDTO[]> => {
    const response = await axiosInstance.get(`/businesses/verifications`);
    return response.data;
};