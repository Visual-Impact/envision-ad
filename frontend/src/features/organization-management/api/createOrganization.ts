import { OrganizationRequestDTO, OrganizationResponseDTO } from "@/entities/organization";
import { axiosInstance } from "@/shared/api";

export const createOrganization = async (
    data: OrganizationRequestDTO
): Promise<OrganizationResponseDTO> => {
    const response = await axiosInstance.post("/businesses", data);
    return response.data;
};
