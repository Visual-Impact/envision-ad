import { Employee } from "@/entities/organization";
import { axiosInstance } from "@/shared/api"; // [cite: 216]

export const getUserClient = async (id: string): Promise<Employee> => {
    const response = await axiosInstance.get(`/api/auth0/get-user/${encodeURIComponent(id)}`);
    return response.data;
};