import { AccountListItem } from "@/entities/account";
import axiosInstance from "@/shared/api/axios/axios";

export const getAllAccounts = async (): Promise<AccountListItem[]> => {
    const response = await axiosInstance.get("/admin/accounts");
    return response.data;
};
