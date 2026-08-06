package com.envisionad.webservice.bundle.businesslogiclayer;

import com.envisionad.webservice.bundle.dataaccesslayer.Bundle;
import com.envisionad.webservice.bundle.dataaccesslayer.BundleExcludedMedia;
import com.envisionad.webservice.bundle.dataaccesslayer.BundleExcludedMediaRepository;
import com.envisionad.webservice.bundle.dataaccesslayer.BundleRepository;
import com.envisionad.webservice.bundle.dataaccesslayer.BundleRuleType;
import com.envisionad.webservice.media.DataAccessLayer.Media;
import com.envisionad.webservice.media.DataAccessLayer.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * The pricing pipeline's composition: chain order, money arithmetic, and the buyer
 * context that P4/P8 will later read.
 *
 * <p>Built with the real filter/modifier beans rather than mocks, since the thing
 * under test is how they compose — mocking them would test only the plumbing.
 */
@ExtendWith(MockitoExtension.class)
class BundlePricingServiceUnitTest {

    private static final String BUNDLE_ID = "bundle-1";

    @Mock
    private BundleService bundleService;

    @Mock
    private BundleExcludedMediaRepository excludedMediaRepository;

    /**
     * Feeds {@link BundleDiscountModifier}. Left unstubbed in most tests: Mockito
     * returns {@code Optional.empty()} for Optional-returning methods, which the
     * modifier reads as "no discount", so the pre-discount expectations still hold.
     */
    @Mock
    private BundleRepository bundleRepository;

    private BundlePricingServiceImpl pricingService;
    private Bundle bundle;

    @BeforeEach
    void setUp() {
        pricingService = new BundlePricingServiceImpl(
                bundleService,
                new ActiveStatusExclusionFilter(),
                new ManualExclusionFilter(excludedMediaRepository),
                new NoOpDiscountModifier(),
                new BundleDiscountModifier(bundleRepository));

        bundle = new Bundle();
        bundle.setBundleId(BUNDLE_ID);
        bundle.setRuleType(BundleRuleType.FULL_NETWORK);
    }

    private Media media(UUID id, Status status, String price) {
        Media media = new Media();
        media.setId(id);
        media.setStatus(status);
        media.setPrice(price == null ? null : new BigDecimal(price));
        return media;
    }

    private void givenRuleMatched(List<Media> medias) {
        when(bundleService.getBundleByBundleId(BUNDLE_ID)).thenReturn(bundle);
        when(bundleService.getRuleMatchedMedias(bundle)).thenReturn(medias);
    }

    private void givenNoExclusions() {
        when(excludedMediaRepository.findAllByIdBundleId(BUNDLE_ID)).thenReturn(List.of());
    }

    @Test
    void sumsUniformPricesToTwoDecimals() {
        givenRuleMatched(List.of(
                media(UUID.randomUUID(), Status.ACTIVE, "4.00"),
                media(UUID.randomUUID(), Status.ACTIVE, "4.00"),
                media(UUID.randomUUID(), Status.ACTIVE, "4.00")));
        givenNoExclusions();

        BundlePriceQuote quote = pricingService.quote(BUNDLE_ID, "business-1");

        assertEquals(3, quote.eligibleMedias().size());
        assertEquals(new BigDecimal("12.00"), quote.basePrice());
        assertEquals(new BigDecimal("12.00"), quote.finalPrice());
    }

    @Test
    void sumsMixedPricesAndScalesHalfUp() {
        givenRuleMatched(List.of(
                media(UUID.randomUUID(), Status.ACTIVE, "4.005"),
                media(UUID.randomUUID(), Status.ACTIVE, "10.50"),
                media(UUID.randomUUID(), Status.ACTIVE, "0.01")));
        givenNoExclusions();

        BundlePriceQuote quote = pricingService.quote(BUNDLE_ID, "business-1");

        // 4.005 + 10.50 + 0.01 = 14.515 -> HALF_UP at 2dp -> 14.52
        assertEquals(new BigDecimal("14.52"), quote.basePrice());
        assertEquals(2, quote.basePrice().scale());
    }

    @Test
    void nullPricedMediaContributesZeroButStillCounts() {
        givenRuleMatched(List.of(
                media(UUID.randomUUID(), Status.ACTIVE, "4.00"),
                media(UUID.randomUUID(), Status.ACTIVE, null)));
        givenNoExclusions();

        BundlePriceQuote quote = pricingService.quote(BUNDLE_ID, "business-1");

        assertEquals(2, quote.eligibleMedias().size(), "a priceless screen is still an eligible screen");
        assertEquals(new BigDecimal("4.00"), quote.basePrice());
    }

    @Test
    void emptyEligibleSetQuotesZero() {
        givenRuleMatched(List.of());

        BundlePriceQuote quote = pricingService.quote(BUNDLE_ID, "business-1");

        assertTrue(quote.eligibleMedias().isEmpty());
        assertEquals(new BigDecimal("0.00"), quote.basePrice());
        assertEquals(new BigDecimal("0.00"), quote.finalPrice());
    }

    @Test
    void discountSplitsBasePriceFromFinalPrice() {
        // The card's "was / now" comparison depends on these two staying distinct.
        bundle.setDiscountPercent(20);
        givenRuleMatched(List.of(
                media(UUID.randomUUID(), Status.ACTIVE, "4.00"),
                media(UUID.randomUUID(), Status.ACTIVE, "4.00")));
        givenNoExclusions();
        when(bundleRepository.findByBundleId(BUNDLE_ID)).thenReturn(java.util.Optional.of(bundle));

        BundlePriceQuote quote = pricingService.quote(BUNDLE_ID, "business-1");

        assertEquals(new BigDecimal("8.00"), quote.basePrice(), "base stays the undiscounted sum");
        assertEquals(new BigDecimal("6.40"), quote.finalPrice(), "final is 20% off");
    }

    @Test
    void discountAppliesAfterTheEligibilityFilters() {
        // Excluded/inactive screens must not be paid for even at a discount.
        UUID excludedId = UUID.randomUUID();
        bundle.setDiscountPercent(50);
        givenRuleMatched(List.of(
                media(UUID.randomUUID(), Status.ACTIVE, "4.00"),
                media(excludedId, Status.ACTIVE, "4.00"),
                media(UUID.randomUUID(), Status.INACTIVE, "100.00")));
        when(excludedMediaRepository.findAllByIdBundleId(BUNDLE_ID))
                .thenReturn(List.of(new BundleExcludedMedia(BUNDLE_ID, excludedId)));
        when(bundleRepository.findByBundleId(BUNDLE_ID)).thenReturn(java.util.Optional.of(bundle));

        BundlePriceQuote quote = pricingService.quote(BUNDLE_ID, "business-1");

        assertEquals(1, quote.eligibleMedias().size());
        assertEquals(new BigDecimal("4.00"), quote.basePrice());
        assertEquals(new BigDecimal("2.00"), quote.finalPrice());
    }

    @Test
    void activeStatusFilterRunsBeforePricing() {
        givenRuleMatched(List.of(
                media(UUID.randomUUID(), Status.ACTIVE, "4.00"),
                media(UUID.randomUUID(), Status.INACTIVE, "99.00"),
                media(UUID.randomUUID(), Status.PENDING, "99.00")));
        givenNoExclusions();

        BundlePriceQuote quote = pricingService.quote(BUNDLE_ID, "business-1");

        assertEquals(1, quote.eligibleMedias().size());
        assertEquals(new BigDecimal("4.00"), quote.basePrice());
    }

    @Test
    void manuallyExcludedMediaIsDroppedFromCountAndPrice() {
        UUID keptId = UUID.randomUUID();
        UUID excludedId = UUID.randomUUID();
        givenRuleMatched(List.of(
                media(keptId, Status.ACTIVE, "4.00"),
                media(excludedId, Status.ACTIVE, "4.00")));
        when(excludedMediaRepository.findAllByIdBundleId(BUNDLE_ID))
                .thenReturn(List.of(new BundleExcludedMedia(BUNDLE_ID, excludedId)));

        BundlePriceQuote quote = pricingService.quote(BUNDLE_ID, "business-1");

        assertEquals(1, quote.eligibleMedias().size());
        assertEquals(keptId, quote.eligibleMedias().get(0).getId());
        assertEquals(new BigDecimal("4.00"), quote.basePrice());
    }

    @Test
    void bothFiltersApplyTogether() {
        UUID excludedId = UUID.randomUUID();
        givenRuleMatched(List.of(
                media(UUID.randomUUID(), Status.ACTIVE, "4.00"),
                media(excludedId, Status.ACTIVE, "4.00"),
                media(UUID.randomUUID(), Status.INACTIVE, "4.00")));
        when(excludedMediaRepository.findAllByIdBundleId(BUNDLE_ID))
                .thenReturn(List.of(new BundleExcludedMedia(BUNDLE_ID, excludedId)));

        BundlePriceQuote quote = pricingService.quote(BUNDLE_ID, "business-1");

        assertEquals(1, quote.eligibleMedias().size());
        assertEquals(new BigDecimal("4.00"), quote.basePrice());
    }

    @Test
    void advertiserBusinessIdReachesTheFilterContext() {
        // v1 filters ignore it, but P4/P8 depend on it being threaded through — so
        // assert the wiring now rather than discovering it missing two projects later.
        ManualExclusionFilter spyFilter = spy(new ManualExclusionFilter(excludedMediaRepository));
        BundlePricingServiceImpl service = new BundlePricingServiceImpl(
                bundleService, new ActiveStatusExclusionFilter(), spyFilter, new NoOpDiscountModifier(), new BundleDiscountModifier(bundleRepository));

        givenRuleMatched(List.of(media(UUID.randomUUID(), Status.ACTIVE, "4.00")));
        givenNoExclusions();

        service.quote(BUNDLE_ID, "business-42");

        ArgumentCaptor<PricingContext> contextCaptor = ArgumentCaptor.forClass(PricingContext.class);
        verify(spyFilter).filter(anyList(), contextCaptor.capture());
        assertEquals(BUNDLE_ID, contextCaptor.getValue().bundleId());
        assertEquals("business-42", contextCaptor.getValue().advertiserBusinessId());
    }

    @Test
    void nullAdvertiserBusinessIdIsThreadedThroughForAnonymousBrowsing() {
        ManualExclusionFilter spyFilter = spy(new ManualExclusionFilter(excludedMediaRepository));
        BundlePricingServiceImpl service = new BundlePricingServiceImpl(
                bundleService, new ActiveStatusExclusionFilter(), spyFilter, new NoOpDiscountModifier(), new BundleDiscountModifier(bundleRepository));

        givenRuleMatched(List.of(media(UUID.randomUUID(), Status.ACTIVE, "4.00")));
        givenNoExclusions();

        BundlePriceQuote quote = service.quote(BUNDLE_ID, null);

        ArgumentCaptor<PricingContext> contextCaptor = ArgumentCaptor.forClass(PricingContext.class);
        verify(spyFilter).filter(anyList(), contextCaptor.capture());
        assertNull(contextCaptor.getValue().advertiserBusinessId());
        assertEquals(new BigDecimal("4.00"), quote.basePrice());
    }

    @Test
    void filtersRunInDeclaredOrder_activeStatusBeforeManualExclusion() {
        ActiveStatusExclusionFilter statusFilter = spy(new ActiveStatusExclusionFilter());
        ManualExclusionFilter exclusionFilter = spy(new ManualExclusionFilter(excludedMediaRepository));
        BundlePricingServiceImpl service = new BundlePricingServiceImpl(
                bundleService, statusFilter, exclusionFilter, new NoOpDiscountModifier(), new BundleDiscountModifier(bundleRepository));

        givenRuleMatched(List.of(
                media(UUID.randomUUID(), Status.ACTIVE, "4.00"),
                media(UUID.randomUUID(), Status.INACTIVE, "4.00")));
        givenNoExclusions();

        service.quote(BUNDLE_ID, "business-1");

        InOrder inOrder = inOrder(statusFilter, exclusionFilter);
        inOrder.verify(statusFilter).filter(anyList(), any());
        // The exclusion filter sees the status filter's output, not the raw candidate set.
        inOrder.verify(exclusionFilter).filter(argThat(medias -> medias.size() == 1), any());
    }

    @Test
    void v1ModifierLeavesFinalPriceEqualToBasePrice() {
        givenRuleMatched(List.of(media(UUID.randomUUID(), Status.ACTIVE, "10.00")));
        givenNoExclusions();

        BundlePriceQuote quote = pricingService.quote(BUNDLE_ID, "business-1");

        assertEquals(quote.basePrice(), quote.finalPrice());
    }

    @Test
    void unknownBundlePropagatesTheNotFoundException() {
        when(bundleService.getBundleByBundleId("nope"))
                .thenThrow(new com.envisionad.webservice.bundle.exceptions.BundleNotFoundException("nope"));

        assertThrows(com.envisionad.webservice.bundle.exceptions.BundleNotFoundException.class,
                () -> pricingService.quote("nope", "business-1"));
    }
}
