package com.envisionad.webservice.bundle.businesslogiclayer;

import com.envisionad.webservice.bundle.dataaccesslayer.Bundle;
import com.envisionad.webservice.media.DataAccessLayer.Media;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class BundlePricingServiceImpl implements BundlePricingService {

    private static final int MONEY_SCALE = 2;

    private final BundleService bundleService;

    /**
     * The eligibility chain, in the order it runs.
     *
     * <p>Deliberately an explicit list rather than an injected
     * {@code List<MediaEligibilityFilter>}: ordering must never depend on bean
     * discovery order. P4 (competitive exclusion) and P8 (sold-out) each add one
     * line here and change nothing else.
     */
    private final List<MediaEligibilityFilter> eligibilityFilters;

    /**
     * Applied to the summed base price, in order. Same explicit-list rule as the
     * filters above: order is declared here, never inferred from bean discovery.
     */
    private final List<BundlePricingModifier> pricingModifiers;

    public BundlePricingServiceImpl(BundleService bundleService,
            ActiveStatusExclusionFilter activeStatusExclusionFilter,
            ManualExclusionFilter manualExclusionFilter,
            NoOpDiscountModifier noOpDiscountModifier,
            BundleDiscountModifier bundleDiscountModifier) {
        this.bundleService = bundleService;
        this.eligibilityFilters = List.of(
                activeStatusExclusionFilter,
                manualExclusionFilter);
        this.pricingModifiers = List.of(
                noOpDiscountModifier,
                bundleDiscountModifier);
    }

    @Override
    public BundlePriceQuote quote(String bundleId, String advertiserBusinessId) {
        Bundle bundle = bundleService.getBundleByBundleId(bundleId);
        PricingContext context = new PricingContext(bundleId, advertiserBusinessId);

        List<Media> eligible = bundleService.getRuleMatchedMedias(bundle);
        for (MediaEligibilityFilter filter : eligibilityFilters) {
            eligible = filter.filter(eligible, context);
        }

        BigDecimal basePrice = sumPrices(eligible);

        BigDecimal finalPrice = basePrice;
        for (BundlePricingModifier modifier : pricingModifiers) {
            finalPrice = modifier.apply(finalPrice, context);
        }

        return new BundlePriceQuote(eligible, basePrice, scale(finalPrice));
    }

    private BigDecimal sumPrices(List<Media> medias) {
        // media.price is nullable in the schema; a screen without a price simply
        // contributes nothing rather than blowing up the whole quote.
        BigDecimal total = medias.stream()
                .map(Media::getPrice)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return scale(total);
    }

    private BigDecimal scale(BigDecimal amount) {
        return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
