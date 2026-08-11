package com.envisionad.webservice.payment.presentationlayer;

import com.envisionad.webservice.payment.businesslogiclayer.CouponService;
import com.envisionad.webservice.payment.dataaccesslayer.Coupon;
import com.envisionad.webservice.payment.mappinglayer.CouponMapper;
import com.envisionad.webservice.payment.presentationlayer.models.CouponRequestDTO;
import com.envisionad.webservice.payment.presentationlayer.models.CouponResponseDTO;
import com.envisionad.webservice.payment.presentationlayer.models.CouponValidateRequestDTO;
import com.envisionad.webservice.payment.presentationlayer.models.CouponValidateResponseDTO;
import com.stripe.exception.StripeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Coupons are pure billing configuration with no independent domain concept, so this
 * lives in {@code payment} rather than a new module (brief §8.1, resolving its own
 * open question #2 in P1's favor — P1 kept everything Stripe-billing-shaped here too).
 */
@RestController
@RequestMapping("/api/v1/coupons")
@CrossOrigin(origins = {"http://localhost:3000", "https://envision-ad.ca"})
public class CouponController {

    private final CouponService couponService;
    private final CouponMapper couponMapper;

    public CouponController(CouponService couponService, CouponMapper couponMapper) {
        this.couponService = couponService;
        this.couponMapper = couponMapper;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('manage:coupons')")
    public ResponseEntity<CouponResponseDTO> createCoupon(
            @AuthenticationPrincipal Jwt jwt, @RequestBody CouponRequestDTO request) throws StripeException {
        Coupon created = couponService.createCoupon(request, jwt.getSubject());
        return ResponseEntity.status(HttpStatus.CREATED).body(couponMapper.entityToResponseDTO(created));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('manage:coupons')")
    public ResponseEntity<List<CouponResponseDTO>> getAllCoupons(
            @RequestParam(required = false, defaultValue = "false") boolean includeArchived) throws StripeException {
        List<CouponResponseDTO> response = couponService.getAllCoupons(includeArchived).stream()
                .map(couponMapper::entityToResponseDTO)
                .toList();
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{couponId}")
    @PreAuthorize("hasAuthority('manage:coupons')")
    public ResponseEntity<CouponResponseDTO> updateCoupon(
            @PathVariable String couponId, @RequestBody CouponRequestDTO request) throws StripeException {
        Coupon updated = couponService.updateCoupon(couponId, request);
        return ResponseEntity.ok(couponMapper.entityToResponseDTO(updated));
    }

    @DeleteMapping("/{couponId}")
    @PreAuthorize("hasAuthority('manage:coupons')")
    public ResponseEntity<Void> archiveCoupon(@PathVariable String couponId) throws StripeException {
        couponService.archiveCoupon(couponId);
        return ResponseEntity.noContent().build();
    }

    /** Any authenticated user, not admin-gated — this is the checkout-side preview
     * (brief §4.6), open to whichever advertiser is checking out. */
    @PostMapping("/validate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CouponValidateResponseDTO> validateCoupon(@RequestBody CouponValidateRequestDTO request) {
        return ResponseEntity.ok(couponService.validateCoupon(request.getCode(), request.getSubtotalCents()));
    }
}
