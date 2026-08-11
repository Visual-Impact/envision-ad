package com.envisionad.webservice.payment.exceptions;

import lombok.Getter;

/**
 * Thrown at subscription-creation time when Stripe rejects a promotion code that
 * passed the earlier client-side preview (brief §4.6.5's "late rejection" case — the
 * validate endpoint itself never throws this, it returns a 200 body with
 * {@code valid: false} instead). {@code code} is one of "invalid" / "expired" /
 * "exhausted", matching the validate endpoint's own error vocabulary so the frontend
 * has one error-handling code path for both the early and late rejection.
 */
@Getter
public class InvalidCouponException extends RuntimeException {

    private final String code;

    public InvalidCouponException(String code, String message) {
        super(message);
        this.code = code;
    }
}
