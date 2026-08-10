package com.envisionad.webservice.payment.mappinglayer;

import com.envisionad.webservice.payment.dataaccesslayer.Coupon;
import com.envisionad.webservice.payment.presentationlayer.models.CouponResponseDTO;
import org.springframework.stereotype.Component;

/** Hand-written, matching {@code BundleSubscriptionResponseMapper} — the module's only other
 * mapper is manual, not MapStruct, so this follows the same convention. */
@Component
public class CouponMapper {

    public CouponResponseDTO entityToResponseDTO(Coupon coupon) {
        CouponResponseDTO response = new CouponResponseDTO();
        response.setCouponId(coupon.getCouponId());
        response.setCode(coupon.getCode());
        response.setDiscountType(coupon.getDiscountType());
        response.setPercentOff(coupon.getPercentOff());
        response.setAmountOffCents(coupon.getAmountOffCents());
        response.setDuration(coupon.getDuration());
        response.setDurationInMonths(coupon.getDurationInMonths());
        response.setExpiresAt(coupon.getExpiresAt());
        response.setMaxRedemptions(coupon.getMaxRedemptions());
        response.setTimesRedeemed(coupon.getTimesRedeemed());
        response.setStatus(coupon.getStatus());
        response.setCreatedAt(coupon.getCreatedAt());
        return response;
    }
}
