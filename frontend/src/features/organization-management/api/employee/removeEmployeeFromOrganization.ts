import { axiosInstance } from "@/shared/api";

export const removeEmployeeFromOrganization = async (
    organizationId: string,
    employeeId: string
): Promise<void> => {
    const response = await axiosInstance.delete(`/businesses/${organizationId}/employees/${encodeURI(employeeId)}`);
    return response.data;
};
