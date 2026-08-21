import axiosInstance from "@/shared/api/axios/axios";

export const resendCredentials = async (businessId: string): Promise<void> => {
    await axiosInstance.post(`/admin/accounts/${businessId}/resend-credentials`);
};
