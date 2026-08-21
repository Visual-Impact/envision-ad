import { AccountListPageResponse } from "@/entities/account";
import axiosInstance from "@/shared/api/axios/axios";

// Server-paginated (P5 M6 fix) — resolving every owner's email in one unpaged load hit
// Auth0's Management API rate limit once the table held more than a handful of rows.
// Sorted newest-first so a freshly created account always lands on page 1.
export const getAllAccounts = async (page: number, size: number): Promise<AccountListPageResponse> => {
    const response = await axiosInstance.get("/admin/accounts", {
        params: { page, size, sort: "dateCreated,desc" },
    });
    return response.data;
};
