package com.envisionad.webservice.payment.businesslogiclayer;

import com.envisionad.webservice.payment.dataaccesslayer.Coupon;
import com.envisionad.webservice.payment.dataaccesslayer.CouponDuration;
import com.envisionad.webservice.payment.dataaccesslayer.CouponRepository;
import com.envisionad.webservice.payment.dataaccesslayer.CouponStatus;
import com.envisionad.webservice.payment.dataaccesslayer.DiscountType;
import com.envisionad.webservice.payment.exceptions.CouponNotFoundException;
import com.envisionad.webservice.payment.exceptions.DuplicateCouponCodeException;
import com.envisionad.webservice.payment.presentationlayer.models.CouponRequestDTO;
import com.envisionad.webservice.payment.presentationlayer.models.CouponValidateResponseDTO;
import com.stripe.exception.StripeException;
import com.stripe.model.PromotionCode;
import com.stripe.model.PromotionCodeCollection;
import com.stripe.param.CouponCreateParams;
import com.stripe.param.PromotionCodeCreateParams;
import com.stripe.param.PromotionCodeListParams;
import com.stripe.param.PromotionCodeUpdateParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit-level throughout because {@code createCoupon}/{@code updateCoupon}/{@code
 * archiveCoupon} all reach Stripe, and {@code BaseIntegrationTest} cannot stub it
 * (same constraint P1's {@code BundlePayoutServiceUnitTest} documents). Only
 * {@code validateCoupon} and the pure guard-rejection paths are Stripe-free, and
 * those are additionally covered by {@code CouponControllerIntegrationTest} against
 * real Postgres.
 */
@ExtendWith(MockitoExtension.class)
class CouponServiceImplUnitTest {

    private CouponServiceImpl service;

    @Mock
    private CouponRepository couponRepository;

    @BeforeEach
    void setUp() {
        service = new CouponServiceImpl(couponRepository);
    }

    // ---- createCoupon ----

    @Test
    void createCoupon_percentOff_createsStripeCouponAndPromotionCodeAndSavesLocalRow() throws StripeException {
        CouponRequestDTO request = percentRequest("WELCOME20", 20.0, CouponDuration.ONCE, null);
        when(couponRepository.findByCodeIgnoreCase("WELCOME20")).thenReturn(Optional.empty());
        when(couponRepository.save(any(Coupon.class))).thenAnswer(inv -> inv.getArgument(0));

        try (MockedStatic<com.stripe.model.Coupon> stripeCoupons = mockStatic(com.stripe.model.Coupon.class);
             MockedStatic<PromotionCode> promotionCodes = mockStatic(PromotionCode.class)) {

            com.stripe.model.Coupon stripeCoupon = mock(com.stripe.model.Coupon.class);
            when(stripeCoupon.getId()).thenReturn("coupon_abc");
            ArgumentCaptor<CouponCreateParams> couponParams = ArgumentCaptor.forClass(CouponCreateParams.class);
            stripeCoupons.when(() -> com.stripe.model.Coupon.create(couponParams.capture())).thenReturn(stripeCoupon);

            PromotionCode promotionCode = mock(PromotionCode.class);
            when(promotionCode.getId()).thenReturn("promo_abc");
            ArgumentCaptor<PromotionCodeCreateParams> promoParams = ArgumentCaptor.forClass(PromotionCodeCreateParams.class);
            promotionCodes.when(() -> PromotionCode.create(promoParams.capture())).thenReturn(promotionCode);

            Coupon saved = service.createCoupon(request, "auth0|admin1");

            assertEquals("WELCOME20", saved.getCode());
            assertEquals(BigDecimal.valueOf(20.0), saved.getPercentOff());
            assertNull(saved.getAmountOffCents());
            assertEquals(CouponStatus.ACTIVE, saved.getStatus());
            assertEquals("coupon_abc", saved.getStripeCouponId());
            assertEquals("promo_abc", saved.getStripePromotionCodeId());
            assertEquals("auth0|admin1", saved.getCreatedBy());

            assertEquals(CouponCreateParams.Duration.ONCE, couponParams.getValue().getDuration());
            assertEquals(BigDecimal.valueOf(20.0), couponParams.getValue().getPercentOff());
            assertEquals("WELCOME20", promoParams.getValue().getCode());
        }
    }

    @Test
    void createCoupon_fixedAmountOff_setsAmountOffCentsAndCurrency() throws StripeException {
        CouponRequestDTO request = new CouponRequestDTO();
        request.setCode("save5");
        request.setDiscountType(DiscountType.FIXED_AMOUNT);
        request.setAmountOffCents(500L);
        request.setDuration(CouponDuration.FOREVER);
        when(couponRepository.findByCodeIgnoreCase("SAVE5")).thenReturn(Optional.empty());
        when(couponRepository.save(any(Coupon.class))).thenAnswer(inv -> inv.getArgument(0));

        try (MockedStatic<com.stripe.model.Coupon> stripeCoupons = mockStatic(com.stripe.model.Coupon.class);
             MockedStatic<PromotionCode> promotionCodes = mockStatic(PromotionCode.class)) {

            com.stripe.model.Coupon stripeCoupon = mock(com.stripe.model.Coupon.class);
            when(stripeCoupon.getId()).thenReturn("coupon_fixed");
            ArgumentCaptor<CouponCreateParams> couponParams = ArgumentCaptor.forClass(CouponCreateParams.class);
            stripeCoupons.when(() -> com.stripe.model.Coupon.create(couponParams.capture())).thenReturn(stripeCoupon);

            PromotionCode promotionCode = mock(PromotionCode.class);
            when(promotionCode.getId()).thenReturn("promo_fixed");
            promotionCodes.when(() -> PromotionCode.create(any(PromotionCodeCreateParams.class))).thenReturn(promotionCode);

            // Code is normalized (trimmed + uppercased) before hitting Stripe or the repository.
            Coupon saved = service.createCoupon(request, "auth0|admin1");

            assertEquals("SAVE5", saved.getCode());
            assertEquals(500L, saved.getAmountOffCents());
            assertNull(saved.getPercentOff());
            assertEquals(500L, couponParams.getValue().getAmountOff());
            assertEquals("cad", couponParams.getValue().getCurrency());
        }
    }

    @Test
    void createCoupon_duplicateCode_throwsBeforeAnyStripeCall() {
        CouponRequestDTO request = percentRequest("EXISTING", 10.0, CouponDuration.ONCE, null);
        when(couponRepository.findByCodeIgnoreCase("EXISTING")).thenReturn(Optional.of(new Coupon()));

        try (MockedStatic<com.stripe.model.Coupon> stripeCoupons = mockStatic(com.stripe.model.Coupon.class)) {
            assertThrows(DuplicateCouponCodeException.class, () -> service.createCoupon(request, "auth0|admin1"));
            stripeCoupons.verifyNoInteractions();
        }
        verify(couponRepository, never()).save(any());
    }

    @Test
    void createCoupon_repeatingWithOneMonth_isRoutedToOnce() throws StripeException {
        CouponRequestDTO request = percentRequest("SHORT", 10.0, CouponDuration.REPEATING, 1);
        when(couponRepository.findByCodeIgnoreCase("SHORT")).thenReturn(Optional.empty());
        when(couponRepository.save(any(Coupon.class))).thenAnswer(inv -> inv.getArgument(0));

        try (MockedStatic<com.stripe.model.Coupon> stripeCoupons = mockStatic(com.stripe.model.Coupon.class);
             MockedStatic<PromotionCode> promotionCodes = mockStatic(PromotionCode.class)) {

            com.stripe.model.Coupon stripeCoupon = mock(com.stripe.model.Coupon.class);
            when(stripeCoupon.getId()).thenReturn("coupon_x");
            ArgumentCaptor<CouponCreateParams> couponParams = ArgumentCaptor.forClass(CouponCreateParams.class);
            stripeCoupons.when(() -> com.stripe.model.Coupon.create(couponParams.capture())).thenReturn(stripeCoupon);

            PromotionCode promotionCode = mock(PromotionCode.class);
            when(promotionCode.getId()).thenReturn("promo_x");
            promotionCodes.when(() -> PromotionCode.create(any(PromotionCodeCreateParams.class))).thenReturn(promotionCode);

            Coupon saved = service.createCoupon(request, "auth0|admin1");

            assertEquals(CouponDuration.ONCE, saved.getDuration());
            assertNull(saved.getDurationInMonths());
            assertEquals(CouponCreateParams.Duration.ONCE, couponParams.getValue().getDuration());
        }
    }

    @Test
    void createCoupon_repeatingOutOfRange_throwsAndNeverCallsStripe() {
        CouponRequestDTO request = percentRequest("TOO-LONG", 10.0, CouponDuration.REPEATING, 13);

        try (MockedStatic<com.stripe.model.Coupon> stripeCoupons = mockStatic(com.stripe.model.Coupon.class)) {
            assertThrows(IllegalArgumentException.class, () -> service.createCoupon(request, "auth0|admin1"));
            stripeCoupons.verifyNoInteractions();
        }
        verifyNoInteractions(couponRepository);
    }

    @Test
    void createCoupon_percentOffOutOfRange_throwsIllegalArgument() {
        CouponRequestDTO request = percentRequest("BADPCT", 150.0, CouponDuration.ONCE, null);
        assertThrows(IllegalArgumentException.class, () -> service.createCoupon(request, "auth0|admin1"));
        verifyNoInteractions(couponRepository);
    }

    @Test
    void createCoupon_invalidCodeCharacters_throwsIllegalArgument() {
        CouponRequestDTO request = percentRequest("bad code!", 10.0, CouponDuration.ONCE, null);
        assertThrows(IllegalArgumentException.class, () -> service.createCoupon(request, "auth0|admin1"));
    }

    @Test
    void createCoupon_promotionCodeCreationFails_rollsBackTheOrphanedStripeCoupon() throws StripeException {
        CouponRequestDTO request = percentRequest("ROLLBACK", 10.0, CouponDuration.ONCE, null);
        when(couponRepository.findByCodeIgnoreCase("ROLLBACK")).thenReturn(Optional.empty());

        try (MockedStatic<com.stripe.model.Coupon> stripeCoupons = mockStatic(com.stripe.model.Coupon.class);
             MockedStatic<PromotionCode> promotionCodes = mockStatic(PromotionCode.class)) {

            com.stripe.model.Coupon stripeCoupon = mock(com.stripe.model.Coupon.class);
            stripeCoupons.when(() -> com.stripe.model.Coupon.create(any(CouponCreateParams.class))).thenReturn(stripeCoupon);
            promotionCodes.when(() -> PromotionCode.create(any(PromotionCodeCreateParams.class)))
                    .thenThrow(mock(com.stripe.exception.InvalidRequestException.class));

            assertThrows(StripeException.class, () -> service.createCoupon(request, "auth0|admin1"));

            verify(stripeCoupon).delete();
        }
        verify(couponRepository, never()).save(any());
    }

    // ---- updateCoupon ----

    @Test
    void updateCoupon_activeFalse_callsStripeAndSetsInactive() throws StripeException {
        Coupon existing = existingCoupon("promo_1", CouponStatus.ACTIVE);
        when(couponRepository.findByCouponId("c-1")).thenReturn(Optional.of(existing));
        when(couponRepository.save(any(Coupon.class))).thenAnswer(inv -> inv.getArgument(0));

        CouponRequestDTO patch = new CouponRequestDTO();
        patch.setActive(false);

        try (MockedStatic<PromotionCode> promotionCodes = mockStatic(PromotionCode.class)) {
            PromotionCode remote = mock(PromotionCode.class);
            promotionCodes.when(() -> PromotionCode.retrieve("promo_1")).thenReturn(remote);
            when(remote.update(any(PromotionCodeUpdateParams.class))).thenReturn(remote);

            Coupon updated = service.updateCoupon("c-1", patch);

            assertEquals(CouponStatus.INACTIVE, updated.getStatus());
            ArgumentCaptor<PromotionCodeUpdateParams> params = ArgumentCaptor.forClass(PromotionCodeUpdateParams.class);
            verify(remote).update(params.capture());
            assertEquals(Boolean.FALSE, params.getValue().getActive());
        }
    }

    @Test
    void updateCoupon_archivedCoupon_throwsWithoutCallingStripe() {
        Coupon archived = existingCoupon("promo_2", CouponStatus.ARCHIVED);
        when(couponRepository.findByCouponId("c-2")).thenReturn(Optional.of(archived));

        try (MockedStatic<PromotionCode> promotionCodes = mockStatic(PromotionCode.class)) {
            assertThrows(IllegalArgumentException.class,
                    () -> service.updateCoupon("c-2", new CouponRequestDTO()));
            promotionCodes.verifyNoInteractions();
        }
    }

    @Test
    void updateCoupon_missingCoupon_throwsCouponNotFound() {
        when(couponRepository.findByCouponId("missing")).thenReturn(Optional.empty());
        assertThrows(CouponNotFoundException.class, () -> service.updateCoupon("missing", new CouponRequestDTO()));
    }

    // ---- archiveCoupon ----

    @Test
    void archiveCoupon_activeCoupon_deactivatesAtStripeAndArchivesLocally() throws StripeException {
        Coupon existing = existingCoupon("promo_3", CouponStatus.ACTIVE);
        when(couponRepository.findByCouponId("c-3")).thenReturn(Optional.of(existing));
        when(couponRepository.save(any(Coupon.class))).thenAnswer(inv -> inv.getArgument(0));

        try (MockedStatic<PromotionCode> promotionCodes = mockStatic(PromotionCode.class)) {
            PromotionCode remote = mock(PromotionCode.class);
            promotionCodes.when(() -> PromotionCode.retrieve("promo_3")).thenReturn(remote);
            when(remote.update(any(PromotionCodeUpdateParams.class))).thenReturn(remote);

            service.archiveCoupon("c-3");

            assertEquals(CouponStatus.ARCHIVED, existing.getStatus());
            ArgumentCaptor<PromotionCodeUpdateParams> params = ArgumentCaptor.forClass(PromotionCodeUpdateParams.class);
            verify(remote).update(params.capture());
            assertEquals(Boolean.FALSE, params.getValue().getActive());
        }
    }

    @Test
    void archiveCoupon_alreadyArchived_isIdempotentAndNeverCallsStripe() throws StripeException {
        Coupon archived = existingCoupon("promo_4", CouponStatus.ARCHIVED);
        when(couponRepository.findByCouponId("c-4")).thenReturn(Optional.of(archived));

        try (MockedStatic<PromotionCode> promotionCodes = mockStatic(PromotionCode.class)) {
            service.archiveCoupon("c-4");
            promotionCodes.verifyNoInteractions();
        }
        verify(couponRepository, never()).save(any());
    }

    // ---- getAllCoupons / resync (brief §4.5) ----

    @Test
    void getAllCoupons_resyncsTimesRedeemedFromStripeForNonArchivedRows() throws StripeException {
        Coupon active = existingCoupon("promo_5", CouponStatus.ACTIVE);
        active.setTimesRedeemed(1);
        when(couponRepository.findAllByStatusNot(CouponStatus.ARCHIVED)).thenReturn(List.of(active));
        when(couponRepository.save(any(Coupon.class))).thenAnswer(inv -> inv.getArgument(0));

        try (MockedStatic<PromotionCode> promotionCodes = mockStatic(PromotionCode.class)) {
            PromotionCode remote = mock(PromotionCode.class);
            when(remote.getId()).thenReturn("promo_5");
            when(remote.getTimesRedeemed()).thenReturn(7L);
            PromotionCodeCollection page = mock(PromotionCodeCollection.class);
            when(page.getData()).thenReturn(List.of(remote));
            promotionCodes.when(() -> PromotionCode.list(any(PromotionCodeListParams.class))).thenReturn(page);

            List<Coupon> result = service.getAllCoupons(false);

            assertEquals(1, result.size());
            assertEquals(7, result.get(0).getTimesRedeemed());
        }
    }

    @Test
    void getAllCoupons_onlyArchivedRows_skipsStripeEntirely() throws StripeException {
        Coupon archived = existingCoupon("promo_6", CouponStatus.ARCHIVED);
        when(couponRepository.findAll()).thenReturn(List.of(archived));

        try (MockedStatic<PromotionCode> promotionCodes = mockStatic(PromotionCode.class)) {
            List<Coupon> result = service.getAllCoupons(true);
            assertEquals(1, result.size());
            promotionCodes.verifyNoInteractions();
        }
    }

    // ---- validateCoupon (brief §4.6.3 — no Stripe call, ever) ----

    @Test
    void validateCoupon_percentOff_computesDiscountAndPreviewTotal() {
        Coupon coupon = existingCoupon("promo_7", CouponStatus.ACTIVE);
        coupon.setDiscountType(DiscountType.PERCENT);
        coupon.setPercentOff(BigDecimal.valueOf(20));
        when(couponRepository.findByCodeIgnoreCase("WELCOME20")).thenReturn(Optional.of(coupon));

        CouponValidateResponseDTO result = service.validateCoupon("welcome20", 1000L);

        assertTrue(result.isValid());
        assertEquals(200L, result.getDiscountAmountCents());
        assertEquals(800L, result.getPreviewTotalCents());
    }

    @Test
    void validateCoupon_fixedAmountLargerThanSubtotal_clampsToZeroNotNegative() {
        Coupon coupon = existingCoupon("promo_8", CouponStatus.ACTIVE);
        coupon.setDiscountType(DiscountType.FIXED_AMOUNT);
        coupon.setAmountOffCents(5000L);
        when(couponRepository.findByCodeIgnoreCase("BIGOFF")).thenReturn(Optional.of(coupon));

        CouponValidateResponseDTO result = service.validateCoupon("BIGOFF", 1000L);

        assertTrue(result.isValid());
        assertEquals(1000L, result.getDiscountAmountCents());
        assertEquals(0L, result.getPreviewTotalCents());
    }

    @Test
    void validateCoupon_unknownCode_returnsInvalid() {
        when(couponRepository.findByCodeIgnoreCase("NOPE")).thenReturn(Optional.empty());
        CouponValidateResponseDTO result = service.validateCoupon("NOPE", 1000L);
        assertFalse(result.isValid());
        assertEquals("invalid", result.getError());
    }

    @Test
    void validateCoupon_inactiveStatus_returnsInvalid() {
        Coupon coupon = existingCoupon("promo_9", CouponStatus.INACTIVE);
        when(couponRepository.findByCodeIgnoreCase("PAUSED")).thenReturn(Optional.of(coupon));
        CouponValidateResponseDTO result = service.validateCoupon("PAUSED", 1000L);
        assertFalse(result.isValid());
        assertEquals("invalid", result.getError());
    }

    @Test
    void validateCoupon_expired_returnsExpired() {
        Coupon coupon = existingCoupon("promo_10", CouponStatus.ACTIVE);
        coupon.setExpiresAt(LocalDateTime.now().minusDays(1));
        when(couponRepository.findByCodeIgnoreCase("OLD")).thenReturn(Optional.of(coupon));
        CouponValidateResponseDTO result = service.validateCoupon("OLD", 1000L);
        assertFalse(result.isValid());
        assertEquals("expired", result.getError());
    }

    @Test
    void validateCoupon_exhausted_returnsExhausted() {
        Coupon coupon = existingCoupon("promo_11", CouponStatus.ACTIVE);
        coupon.setMaxRedemptions(5);
        coupon.setTimesRedeemed(5);
        when(couponRepository.findByCodeIgnoreCase("DONE")).thenReturn(Optional.of(coupon));
        CouponValidateResponseDTO result = service.validateCoupon("DONE", 1000L);
        assertFalse(result.isValid());
        assertEquals("exhausted", result.getError());
    }

    // ---- helpers ----

    private static CouponRequestDTO percentRequest(
            String code, double percentOff, CouponDuration duration, Integer durationInMonths) {
        CouponRequestDTO request = new CouponRequestDTO();
        request.setCode(code);
        request.setDiscountType(DiscountType.PERCENT);
        request.setPercentOff(percentOff);
        request.setDuration(duration);
        request.setDurationInMonths(durationInMonths);
        return request;
    }

    private static Coupon existingCoupon(String stripePromotionCodeId, CouponStatus status) {
        Coupon coupon = new Coupon();
        coupon.setCouponId("c-" + stripePromotionCodeId);
        coupon.setCode("SOME-CODE");
        coupon.setDiscountType(DiscountType.PERCENT);
        coupon.setPercentOff(BigDecimal.valueOf(10));
        coupon.setDuration(CouponDuration.ONCE);
        coupon.setStatus(status);
        coupon.setStripeCouponId("stripe_coupon_" + stripePromotionCodeId);
        coupon.setStripePromotionCodeId(stripePromotionCodeId);
        return coupon;
    }
}
