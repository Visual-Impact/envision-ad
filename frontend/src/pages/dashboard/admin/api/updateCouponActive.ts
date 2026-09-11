import { Coupon, CouponPatchDTO } from "@/entities/coupon";
import { axiosInstance } from "@/shared/api";

/** Named for what it actually does — `active` is the only field the backend applies
 * from a PATCH (see CouponRequestDTO's doc comment). */
export const updateCouponActive = async (couponId: string, data: CouponPatchDTO): Promise<Coupon> => {
    const response = await axiosInstance.patch(`/coupons/${couponId}`, data);
    return response.data;
};
