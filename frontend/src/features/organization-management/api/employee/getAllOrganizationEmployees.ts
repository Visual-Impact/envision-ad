import { Employee } from "@/entities/organization";
import { axiosInstance } from "@/shared/api";

export const getAllOrganizationEmployees = async (organizationId: string): Promise<Employee[]> => {
    const response = await axiosInstance.get<Array<{employeeId: string; userId: string}>>(`/businesses/${organizationId}/employees`);
    return response.data.map((emp) => ({
        employee_id: emp.employeeId,
        user_id: emp.userId
    }));
};

export const getAllOrganizationEmployeesServer = async (businessId: string, token: string): Promise<Employee[]> => {
    const baseUrl = process.env.DOCKER === "true" ? process.env.WEBSERVICE_API_URL : process.env.NEXT_PUBLIC_API_URL;
    const response = await fetch(
        `${baseUrl}/businesses/${businessId}/employees`,
        {
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json',
            },
        }
    );

    if (!response.ok) {
        console.error(response);
        throw new Error(`Failed to fetch employees: ${response.statusText}`);
    }

    const data: Array<{employeeId: string; userId: string}> = await response.json();
    return data.map((emp) => ({
        employee_id: emp.employeeId,
        user_id: emp.userId
    }));
}
