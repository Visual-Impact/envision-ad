package com.envisionad.webservice.payment.presentationlayer;

import com.envisionad.webservice.config.BaseIntegrationTest;
import com.envisionad.webservice.payment.dataaccesslayer.Coupon;
import com.envisionad.webservice.payment.dataaccesslayer.CouponDuration;
import com.envisionad.webservice.payment.dataaccesslayer.CouponRepository;
import com.envisionad.webservice.payment.dataaccesslayer.CouponStatus;
import com.envisionad.webservice.payment.dataaccesslayer.DiscountType;
import com.envisionad.webservice.payment.presentationlayer.models.CouponRequestDTO;
import com.envisionad.webservice.payment.presentationlayer.models.CouponResponseDTO;
import com.envisionad.webservice.payment.presentationlayer.models.CouponValidateRequestDTO;
import com.envisionad.webservice.payment.presentationlayer.models.CouponValidateResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Every path here is exercised without a real Stripe call — {@code createCoupon}/
 * {@code updateCoupon(active=...)}/{@code archiveCoupon(active row)} all reach Stripe
 * and are covered instead by {@code CouponServiceImplUnitTest} with {@code
 * MockedStatic}, since {@code BaseIntegrationTest} cannot stub Stripe (see its class
 * doc). What's left — validation guards, 404s, the idempotent already-archived
 * no-Stripe-call path, and the fully local {@code /validate} endpoint — is still
 * real coverage against real Postgres.
 */
class CouponControllerIntegrationTest extends BaseIntegrationTest {

    private static final String BASE_URI = "/api/v1/coupons";

    @Autowired
    private CouponRepository couponRepository;

    @BeforeEach
    void setUp() {
        couponRepository.deleteAll();

        Jwt adminJwt = Jwt.withTokenValue("mock-token")
                .header("alg", "none")
                .claim("sub", "auth0|admin123")
                .claim("permissions", List.of("manage:coupons"))
                .build();
        when(jwtDecoder.decode(anyString())).thenReturn(adminJwt);
    }

    private Coupon seedCoupon(String code, CouponStatus status) {
        Coupon coupon = new Coupon();
        coupon.setCode(code);
        coupon.setDiscountType(DiscountType.PERCENT);
        coupon.setPercentOff(BigDecimal.valueOf(15));
        coupon.setDuration(CouponDuration.ONCE);
        coupon.setStatus(status);
        coupon.setStripeCouponId("stripe_coupon_" + code);
        coupon.setStripePromotionCodeId("stripe_promo_" + code);
        return couponRepository.save(coupon);
    }

    @Test
    void createCoupon_withoutManageCouponsPermission_isForbidden() {
        when(jwtDecoder.decode(anyString())).thenReturn(
                Jwt.withTokenValue("mock-token")
                        .header("alg", "none")
                        .claim("sub", "auth0|user123")
                        .claim("permissions", List.of())
                        .build());

        CouponRequestDTO request = new CouponRequestDTO();
        request.setCode("NOPERM");
        request.setDiscountType(DiscountType.PERCENT);
        request.setPercentOff(10.0);
        request.setDuration(CouponDuration.ONCE);

        webTestClient.post()
                .uri(BASE_URI)
                .header("Authorization", "Bearer mock-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void createCoupon_duplicateCode_returnsBadRequestWithoutReachingStripe() {
        seedCoupon("EXISTING", CouponStatus.ACTIVE);

        CouponRequestDTO request = new CouponRequestDTO();
        request.setCode("existing"); // lowercase — the duplicate check is case-insensitive
        request.setDiscountType(DiscountType.PERCENT);
        request.setPercentOff(10.0);
        request.setDuration(CouponDuration.ONCE);

        webTestClient.post()
                .uri(BASE_URI)
                .header("Authorization", "Bearer mock-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();

        assertEquals(1, couponRepository.findAll().size());
    }

    @Test
    void createCoupon_invalidDiscountShape_returnsBadRequest() {
        CouponRequestDTO request = new CouponRequestDTO();
        request.setCode("BADSHAPE");
        request.setDiscountType(DiscountType.PERCENT);
        request.setPercentOff(null); // missing — required for PERCENT
        request.setDuration(CouponDuration.ONCE);

        webTestClient.post()
                .uri(BASE_URI)
                .header("Authorization", "Bearer mock-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void getAllCoupons_archivedOnly_returnsThemWithoutTouchingStripe() {
        seedCoupon("GONE", CouponStatus.ARCHIVED);

        webTestClient.get()
                .uri(BASE_URI + "?includeArchived=true")
                .header("Authorization", "Bearer mock-token")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(CouponResponseDTO.class)
                .hasSize(1)
                .value(coupons -> assertEquals(CouponStatus.ARCHIVED, coupons.get(0).getStatus()));
    }

    @Test
    void getAllCoupons_defaultExcludesArchived() {
        seedCoupon("HIDDEN", CouponStatus.ARCHIVED);

        webTestClient.get()
                .uri(BASE_URI)
                .header("Authorization", "Bearer mock-token")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(CouponResponseDTO.class)
                .hasSize(0);
    }

    @Test
    void archiveCoupon_alreadyArchived_returnsNoContentIdempotently() {
        Coupon archived = seedCoupon("ALREADYGONE", CouponStatus.ARCHIVED);

        webTestClient.delete()
                .uri(BASE_URI + "/{couponId}", archived.getCouponId())
                .header("Authorization", "Bearer mock-token")
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void archiveCoupon_unknownCouponId_returnsNotFound() {
        webTestClient.delete()
                .uri(BASE_URI + "/{couponId}", "does-not-exist")
                .header("Authorization", "Bearer mock-token")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void updateCoupon_archivedCoupon_returnsBadRequest() {
        Coupon archived = seedCoupon("CANTEDIT", CouponStatus.ARCHIVED);

        CouponRequestDTO patch = new CouponRequestDTO();
        patch.setActive(true);

        webTestClient.patch()
                .uri(BASE_URI + "/{couponId}", archived.getCouponId())
                .header("Authorization", "Bearer mock-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(patch)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void validateCoupon_activeCoupon_returnsDiscountPreviewWithoutAdminPermission() {
        seedCoupon("SAVE15", CouponStatus.ACTIVE);
        // Authenticated but non-admin — the validate endpoint is open to any advertiser.
        when(jwtDecoder.decode(anyString())).thenReturn(
                Jwt.withTokenValue("mock-token")
                        .header("alg", "none")
                        .claim("sub", "auth0|advertiser1")
                        .claim("permissions", List.of())
                        .build());

        CouponValidateRequestDTO request = new CouponValidateRequestDTO();
        request.setCode("save15");
        request.setSubtotalCents(2000L);

        webTestClient.post()
                .uri(BASE_URI + "/validate")
                .header("Authorization", "Bearer mock-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(CouponValidateResponseDTO.class)
                .value(response -> {
                    assertTrue(response.isValid());
                    assertEquals(300L, response.getDiscountAmountCents());
                    assertEquals(1700L, response.getPreviewTotalCents());
                });
    }

    @Test
    void validateCoupon_unknownCode_returnsInvalidNotAnHttpError() {
        CouponValidateRequestDTO request = new CouponValidateRequestDTO();
        request.setCode("NOSUCHCODE");
        request.setSubtotalCents(1000L);

        webTestClient.post()
                .uri(BASE_URI + "/validate")
                .header("Authorization", "Bearer mock-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(CouponValidateResponseDTO.class)
                .value(response -> {
                    assertFalse(response.isValid());
                    assertEquals("invalid", response.getError());
                });
    }
}
