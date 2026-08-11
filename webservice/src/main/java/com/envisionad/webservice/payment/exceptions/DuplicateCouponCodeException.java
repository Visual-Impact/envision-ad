package com.envisionad.webservice.payment.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * {@code code} is a hard UNIQUE column spanning every status including ARCHIVED
 * (brief §4.1.a) — this fires the same whether the clash is with a live coupon or a
 * long-archived one. Treated as a normal 400 validation error, not a 409, per the
 * brief's own framing of "code already exists" as form validation.
 */
@ResponseStatus(code = HttpStatus.BAD_REQUEST, reason = "Coupon code already exists")
public class DuplicateCouponCodeException extends RuntimeException {
    public DuplicateCouponCodeException(String code) {
        super("Coupon code " + code + " already exists.");
    }
}
