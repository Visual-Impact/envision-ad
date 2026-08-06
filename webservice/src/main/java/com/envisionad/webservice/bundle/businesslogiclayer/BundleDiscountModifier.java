package com.envisionad.webservice.bundle.businesslogiclayer;

import com.envisionad.webservice.bundle.dataaccesslayer.Bundle;
import com.envisionad.webservice.bundle.dataaccesslayer.BundleRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Applies the bundle's admin-set percentage discount to the summed screen price —
 * the real implementation of the modifier stage that {@code NoOpDiscountModifier}
 * held open (brief §7, "P2 replaces/adds to this").
 *
 * <p>This only changes what the advertiser is charged. Media-owner payouts read
 * {@code bundle_subscription_items}, which freeze each screen's full
 * {@code media.price} at signup, so the platform absorbs the discount out of its own
 * fee. A discount above {@code stripe.platform-fee-percent} therefore pays owners
 * more than the subscription collects; the admin form warns past that point.
 */
@Component
public class BundleDiscountModifier implements BundlePricingModifier {

    private static final int MONEY_SCALE = 2;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final BundleRepository bundleRepository;

    public BundleDiscountModifier(BundleRepository bundleRepository) {
        this.bundleRepository = bundleRepository;
    }

    @Override
    public BigDecimal apply(BigDecimal baseAmount, PricingContext context) {
        int discountPercent = bundleRepository.findByBundleId(context.bundleId())
                .map(Bundle::getDiscountPercent)
                .orElse(0);

        if (discountPercent <= 0) {
            return baseAmount;
        }

        BigDecimal multiplier = HUNDRED.subtract(BigDecimal.valueOf(discountPercent))
                .divide(HUNDRED, 4, RoundingMode.HALF_UP);

        return baseAmount.multiply(multiplier).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
