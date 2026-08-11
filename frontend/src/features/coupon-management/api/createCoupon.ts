import { Coupon, CouponRequestDTO } from "@/entities/coupon";
import axiosInstance from "@/shared/api/axios/axios";

export const createCoupon = async (data: CouponRequestDTO): Promise<Coupon> => {
    const response = await axiosInstance.post("/coupons", data);
    return response.data;
};
