import { OrganizationRequestDTO, OrganizationResponseDTO } from "@/entities/organization";
import { axiosInstance } from "@/shared/api";

export const updateOrganization = async (
    id: string,
    data: OrganizationRequestDTO
): Promise<OrganizationResponseDTO> => {
    const response = await axiosInstance.put(`/businesses/${id}`, data);
    return response.data;
};

