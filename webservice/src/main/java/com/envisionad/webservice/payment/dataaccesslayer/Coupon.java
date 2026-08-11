package com.envisionad.webservice.payment.dataaccesslayer;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A local mirror of a Stripe {@code Coupon} + {@code PromotionCode} pair. Discount shape
 * ({@link #discountType}, {@link #percentOff}/{@link #amountOffCents}, {@link #duration},
 * {@link #durationInMonths}) is immutable after creation — Stripe Coupons can't be edited,
 * and neither can this row's equivalent fields (brief §4.2). {@link #code} is a hard
 * {@code UNIQUE} across every status including ARCHIVED: codes are never recycled.
 */
@Entity
@Table(name = "coupons")
@Data
@NoArgsConstructor
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "coupon_id", unique = true, nullable = false, length = 36)
    private String couponId;

    @Column(name = "code", unique = true, nullable = false, length = 40)
    private String code;

    @Column(name = "discount_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private DiscountType discountType;

    @Column(name = "percent_off", precision = 5, scale = 2)
    private BigDecimal percentOff;

    @Column(name = "amount_off_cents")
    private Long amountOffCents;

    @Column(name = "duration", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private CouponDuration duration;

    @Column(name = "duration_in_months")
    private Integer durationInMonths;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "max_redemptions")
    private Integer maxRedemptions;

    @Column(name = "times_redeemed", nullable = false)
    private int timesRedeemed = 0;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private CouponStatus status = CouponStatus.ACTIVE;

    @Column(name = "stripe_coupon_id", unique = true, nullable = false)
    private String stripeCouponId;

    @Column(name = "stripe_promotion_code_id", unique = true, nullable = false)
    private String stripePromotionCodeId;

    @Column(name = "created_by")
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    private void generateCouponId() {
        if (this.couponId == null) {
            this.couponId = java.util.UUID.randomUUID().toString();
        }
    }
}
