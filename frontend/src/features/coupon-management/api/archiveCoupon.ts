import axiosInstance from "@/shared/api/axios/axios";

export const archiveCoupon = async (couponId: string): Promise<void> => {
    await axiosInstance.delete(`/coupons/${couponId}`);
};
