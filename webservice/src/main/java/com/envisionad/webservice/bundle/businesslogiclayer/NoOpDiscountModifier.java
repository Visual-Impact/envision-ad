package com.envisionad.webservice.bundle.businesslogiclayer;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * v1 identity modifier. P2 (bundle discounts) replaces or supplements it; the
 * modifier stage exists now so that adding one later touches no other class.
 */
@Component
public class NoOpDiscountModifier implements BundlePricingModifier {

    @Override
    public BigDecimal apply(BigDecimal baseAmount, PricingContext context) {
        return baseAmount;
    }
}
