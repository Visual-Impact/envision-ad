package com.envisionad.webservice.payment.dataaccesslayer;

/**
 * ARCHIVED is terminal — a code is retired forever once archived (brief §4.4), it never
 * returns to ACTIVE/INACTIVE and its {@code code} is never reissued.
 */
public enum CouponStatus {
    ACTIVE,
    INACTIVE,
    ARCHIVED
}
