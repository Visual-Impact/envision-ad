package com.envisionad.webservice.payment.presentationlayer.models;

import com.envisionad.webservice.payment.dataaccesslayer.CouponDuration;
import com.envisionad.webservice.payment.dataaccesslayer.CouponStatus;
import com.envisionad.webservice.payment.dataaccesslayer.DiscountType;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class CouponResponseDTO {
    private String couponId;
    private String code;
    private DiscountType discountType;
    private BigDecimal percentOff;
    private Long amountOffCents;
    private CouponDuration duration;
    private Integer durationInMonths;
    private LocalDateTime expiresAt;
    private Integer maxRedemptions;
    private int timesRedeemed;
    private CouponStatus status;
    private LocalDateTime createdAt;
}
