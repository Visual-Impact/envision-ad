import { axiosInstance } from "@/shared/api";

export const createStripeConnection = async (businessId: string) => {
    const response = await axiosInstance.post('/payments/connect-account', {
        businessId,
        returnUrl: `${window.location.origin}/dashboard/stripe?onboarding=success`,
        refreshUrl: `${window.location.origin}/dashboard/stripe?onboarding=refresh`,
    });
    return response.data;
};
