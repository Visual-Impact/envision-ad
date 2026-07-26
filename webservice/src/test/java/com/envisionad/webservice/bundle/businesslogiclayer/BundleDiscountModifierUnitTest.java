package com.envisionad.webservice.bundle.businesslogiclayer;

import com.envisionad.webservice.bundle.dataaccesslayer.Bundle;
import com.envisionad.webservice.bundle.dataaccesslayer.BundleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * The discount stage of the pricing pipeline. Money rules apply: BigDecimal,
 * HALF_UP, 2 decimal places.
 */
@ExtendWith(MockitoExtension.class)
class BundleDiscountModifierUnitTest {

    private static final String BUNDLE_ID = "bundle-1";
    private static final PricingContext CONTEXT = new PricingContext(BUNDLE_ID, "business-1");

    @Mock
    private BundleRepository bundleRepository;

    @InjectMocks
    private BundleDiscountModifier modifier;

    private void givenDiscount(int percent) {
        Bundle bundle = new Bundle();
        bundle.setBundleId(BUNDLE_ID);
        bundle.setDiscountPercent(percent);
        when(bundleRepository.findByBundleId(BUNDLE_ID)).thenReturn(Optional.of(bundle));
    }

    @ParameterizedTest(name = "{0} discounted by {1}% = {2}")
    @CsvSource({
            "56.00, 20, 44.80",
            "100.00, 10, 90.00",
            "56.00, 25, 42.00",
            "10.50, 15, 8.93",   // 8.925 -> HALF_UP -> 8.93
            "4.00, 33, 2.68",    // 2.68 exactly
            "56.00, 100, 0.00",
    })
    void appliesThePercentageWithHalfUpRoundingToTwoDecimals(String base, int percent, String expected) {
        givenDiscount(percent);

        BigDecimal result = modifier.apply(new BigDecimal(base), CONTEXT);

        assertEquals(new BigDecimal(expected), result);
        assertEquals(2, result.scale(), "money is always 2dp");
    }

    @Test
    void zeroDiscountReturnsTheBaseAmountUntouched() {
        givenDiscount(0);
        BigDecimal base = new BigDecimal("56.00");

        assertSame(base, modifier.apply(base, CONTEXT));
    }

    @Test
    void unknownBundleIsTreatedAsNoDiscount() {
        // Defensive: the pricing service resolves the bundle first, so this should be
        // unreachable — but silently charging full price beats throwing mid-quote.
        when(bundleRepository.findByBundleId(BUNDLE_ID)).thenReturn(Optional.empty());
        BigDecimal base = new BigDecimal("56.00");

        assertSame(base, modifier.apply(base, CONTEXT));
    }

    @Test
    void zeroBaseStaysZero() {
        givenDiscount(20);

        assertEquals(0, BigDecimal.ZERO.compareTo(modifier.apply(BigDecimal.ZERO, CONTEXT)));
    }

    @Test
    void discountIsLookedUpForTheContextBundle() {
        givenDiscount(10);

        modifier.apply(new BigDecimal("10.00"), CONTEXT);

        verify(bundleRepository).findByBundleId(BUNDLE_ID);
        verifyNoMoreInteractions(bundleRepository);
    }
}
