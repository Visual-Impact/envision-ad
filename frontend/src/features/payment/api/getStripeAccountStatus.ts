import { axiosInstance } from "@/shared/api";

export const getStripeAccountStatus = async (businessId: string) => {
    const response = await axiosInstance.get('/payments/account-status', {
        params: { businessId }
    });
    return response.data;
};
