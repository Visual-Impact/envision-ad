package com.envisionad.webservice.payment.businesslogiclayer;

import com.envisionad.webservice.payment.dataaccesslayer.Coupon;
import com.envisionad.webservice.payment.presentationlayer.models.CouponRequestDTO;
import com.envisionad.webservice.payment.presentationlayer.models.CouponValidateResponseDTO;
import com.stripe.exception.StripeException;

import java.util.List;

public interface CouponService {

    Coupon createCoupon(CouponRequestDTO request, String createdByUserId) throws StripeException;

    /**
     * @param includeArchived when false (the admin table's default view), archived rows are
     *                        omitted and are not resynced against Stripe (brief §4.4.3, §4.5).
     */
    List<Coupon> getAllCoupons(boolean includeArchived) throws StripeException;

    Coupon getCouponByCouponId(String couponId);

    /**
     * Only {@code active} is applied. The brief (§4.2/§6.2) also lists {@code expiresAt} and
     * {@code maxRedemptions} as editable, but Stripe's real {@code PromotionCodeUpdateParams}
     * (verified against the installed SDK) exposes no such setters — both are create-only at
     * Stripe's own API level, so they join the rest of the discount shape as immutable here too
     * (archive + recreate to change them). See P3-PROGRESS.md D5.
     */
    Coupon updateCoupon(String couponId, CouponRequestDTO request) throws StripeException;

    /** Always archives (brief §4.4) — there is no other delete path. */
    void archiveCoupon(String couponId) throws StripeException;

    /** Does not call Stripe (brief §4.6.3) — validated against the locally cached row only. */
    CouponValidateResponseDTO validateCoupon(String code, long subtotalCents);
}
