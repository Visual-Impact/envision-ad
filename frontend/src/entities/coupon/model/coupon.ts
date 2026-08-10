export type DiscountType = "PERCENT" | "FIXED_AMOUNT";
export type CouponDuration = "ONCE" | "REPEATING" | "FOREVER";
export type CouponStatus = "ACTIVE" | "INACTIVE" | "ARCHIVED";

export interface Coupon {
    couponId: string;
    code: string;
    discountType: DiscountType;
    percentOff: number | null;
    amountOffCents: number | null;
    duration: CouponDuration;
    durationInMonths: number | null;
    expiresAt: string | null;
    maxRedemptions: number | null;
    timesRedeemed: number;
    status: CouponStatus;
    createdAt: string;
}

/** Create-only — the backend rejects all of this on edit except via a separate PATCH.
 * Discount shape is immutable at Stripe's own API level once created (see backend
 * CouponService.updateCoupon doc — expiresAt/maxRedemptions are NOT editable either,
 * despite what an earlier draft of the brief assumed). */
export interface CouponRequestDTO {
    code: string;
    discountType: DiscountType;
    percentOff?: number;
    amountOffCents?: number;
    duration: CouponDuration;
    durationInMonths?: number;
    expiresAt?: string;
    maxRedemptions?: number;
}

/** The only thing genuinely editable after creation. */
export interface CouponPatchDTO {
    active: boolean;
}

export type CouponValidateError = "invalid" | "expired" | "exhausted";

/** Response of POST /coupons/validate — a fast, Stripe-free preview (brief §4.6.3). */
export interface CouponValidateResponse {
    valid: boolean;
    discountAmountCents: number | null;
    previewTotalCents: number | null;
    error: CouponValidateError | null;
}
