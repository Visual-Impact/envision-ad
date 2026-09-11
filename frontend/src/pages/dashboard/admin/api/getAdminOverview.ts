import { axiosInstance } from "@/shared/api";

export interface AdminOverviewResponse {
    totalPlatformRevenue: number;
    totalOrganizations: number;
    totalMediaListings: number;
    totalUsers: number;
    totalMediaOwners: number;
    totalAdvertisers: number;
}

export const getAdminOverview = async (): Promise<AdminOverviewResponse> => {
    const response = await axiosInstance.get<AdminOverviewResponse>(
        "/admin/dashboard/overview"
    );

    return response.data;
};
