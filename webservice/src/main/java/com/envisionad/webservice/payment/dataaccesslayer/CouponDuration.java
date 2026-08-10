package com.envisionad.webservice.payment.dataaccesslayer;

/**
 * Maps directly to Stripe {@code Coupon.duration}. {@code REPEATING} always carries a
 * {@code durationInMonths} of 2-12 — a value of 1 is disallowed in the admin form and routed
 * to {@code ONCE} instead, since the two are otherwise indistinguishable (brief §5.3).
 */
public enum CouponDuration {
    ONCE,
    REPEATING,
    FOREVER
}
