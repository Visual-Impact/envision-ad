package com.envisionad.webservice.payment.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.NOT_FOUND, reason = "Coupon not found")
public class CouponNotFoundException extends RuntimeException {
    public CouponNotFoundException(String couponId) {
        super("Coupon with ID " + couponId + " not found.");
    }
}
