package com.envisionad.webservice.payment.presentationlayer.models;

import lombok.Data;
import lombok.NoArgsConstructor;

/** {@code subtotalCents} is the bundle's post-exclusion, post-bundle-discount price the
 * advertiser would pay with no coupon — i.e. {@code BundlePriceQuote.finalPrice()}, in cents. */
@Data
@NoArgsConstructor
public class CouponValidateRequestDTO {
    private String code;
    private long subtotalCents;
}
