package com.envisionad.webservice.bundle.businesslogiclayer;

import java.math.BigDecimal;

public interface BundlePricingModifier {
    // Applied to the summed price after eligibility filtering (e.g. bundle discount %).
    // Coupon codes (P3) are NOT a modifier here — they apply at Stripe Checkout, not in this service.
    BigDecimal apply(BigDecimal baseAmount, PricingContext context);
}
