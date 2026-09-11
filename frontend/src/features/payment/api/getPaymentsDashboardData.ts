import { axiosInstance } from "@/shared/api";

export interface StripeDashboardPayout {
    id?: string;
    amount?: number | string;
    net?: number | string;
    created?: number | string;
    availableOn?: number | string;
    currency?: string;
}

export interface PaymentsDashboardData {
    payouts?: StripeDashboardPayout[];
}

export const getPaymentsDashboardData = async (
    businessId: string,
    period: "weekly" | "monthly" | "yearly" = "monthly"
): Promise<PaymentsDashboardData> => {
    const response = await axiosInstance.get<PaymentsDashboardData>("/payments/dashboard", {
        params: { businessId, period },
    });
    return response.data;
};
