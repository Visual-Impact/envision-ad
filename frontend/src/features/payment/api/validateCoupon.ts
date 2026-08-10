import { CouponValidateResponse } from "@/entities/coupon";
import axiosInstance from "@/shared/api/axios/axios";

/** Checkout-side preview — no Stripe call on the backend, safe to call on every Apply
 * click (brief §4.6.3). `subtotalCents` is the bundle's post-exclusion,
 * post-bundle-discount price the advertiser would pay with no coupon. */
export const validateCoupon = async (code: string, subtotalCents: number): Promise<CouponValidateResponse> => {
    const response = await axiosInstance.post<CouponValidateResponse>("/coupons/validate", {
        code,
        subtotalCents,
    });
    return response.data;
};
