import { axiosInstance } from "@/shared/api";

export const archiveCoupon = async (couponId: string): Promise<void> => {
    await axiosInstance.delete(`/coupons/${couponId}`);
};
