package com.envisionad.webservice.payment.presentationlayer.models;

import com.envisionad.webservice.payment.dataaccesslayer.CouponDuration;
import com.envisionad.webservice.payment.dataaccesslayer.DiscountType;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Doubles as the create body (all fields except {@link #active} apply) and the PATCH
 * body (only {@link #active}, {@link #expiresAt}, {@link #maxRedemptions} apply — the
 * rest of the discount shape is immutable after creation, per brief §4.2, and is
 * ignored by {@code CouponService#updateCoupon}).
 */
@Data
@NoArgsConstructor
public class CouponRequestDTO {
    private String code;
    private DiscountType discountType;
    private Double percentOff;
    private Long amountOffCents;
    private CouponDuration duration;
    private Integer durationInMonths;
    private LocalDateTime expiresAt;
    private Integer maxRedemptions;

    /** PATCH only. Null on create, where a coupon always starts ACTIVE. */
    private Boolean active;
}
