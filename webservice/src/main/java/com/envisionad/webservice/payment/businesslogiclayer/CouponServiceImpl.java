package com.envisionad.webservice.payment.businesslogiclayer;

import com.envisionad.webservice.payment.dataaccesslayer.Coupon;
import com.envisionad.webservice.payment.dataaccesslayer.CouponDuration;
import com.envisionad.webservice.payment.dataaccesslayer.CouponRepository;
import com.envisionad.webservice.payment.dataaccesslayer.CouponStatus;
import com.envisionad.webservice.payment.dataaccesslayer.DiscountType;
import com.envisionad.webservice.payment.exceptions.DuplicateCouponCodeException;
import com.envisionad.webservice.payment.exceptions.CouponNotFoundException;
import com.envisionad.webservice.payment.presentationlayer.models.CouponRequestDTO;
import com.envisionad.webservice.payment.presentationlayer.models.CouponValidateResponseDTO;
import com.stripe.exception.StripeException;
import com.stripe.model.PromotionCode;
import com.stripe.model.PromotionCodeCollection;
import com.stripe.param.CouponCreateParams;
import com.stripe.param.PromotionCodeCreateParams;
import com.stripe.param.PromotionCodeListParams;
import com.stripe.param.PromotionCodeUpdateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Slf4j
@Service
public class CouponServiceImpl implements CouponService {

    private static final int CODE_MIN_LENGTH = 3;
    private static final int CODE_MAX_LENGTH = 40;
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9_-]+$");
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    /** Stripe list page size for the §4.5 redemption-count resync. Coupon admin traffic is
     * low-volume (brief §4.5), so a single unpaginated page is treated as sufficient rather
     * than looping — beyond 100 live coupons this would need to walk pages via
     * {@code starting_after}. */
    private static final long RESYNC_PAGE_LIMIT = 100L;

    private final CouponRepository couponRepository;

    public CouponServiceImpl(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    @Override
    @Transactional
    public Coupon createCoupon(CouponRequestDTO request, String createdByUserId) throws StripeException {
        String code = normalizeCode(request.getCode());
        validateDiscountShape(request.getDiscountType(), request.getPercentOff(), request.getAmountOffCents());
        CouponDuration duration = normalizeDuration(request.getDuration(), request.getDurationInMonths());
        Integer durationInMonths = duration == CouponDuration.REPEATING ? request.getDurationInMonths() : null;

        // Global, permanent uniqueness including archived rows (brief §4.1.a) — the
        // repository query and the DB UNIQUE constraint both span every status.
        if (couponRepository.findByCodeIgnoreCase(code).isPresent()) {
            throw new DuplicateCouponCodeException(code);
        }

        com.stripe.model.Coupon stripeCoupon = createStripeCoupon(
                request.getDiscountType(), request.getPercentOff(), request.getAmountOffCents(),
                duration, durationInMonths);

        PromotionCode promotionCode;
        try {
            promotionCode = createStripePromotionCode(
                    stripeCoupon.getId(), code, request.getExpiresAt(), request.getMaxRedemptions());
        } catch (StripeException e) {
            // Roll back the now-orphaned Coupon rather than leaving an unreachable Stripe
            // object behind — brief §4.1.4. Deletion is allowed while a Coupon is unused.
            try {
                stripeCoupon.delete();
            } catch (StripeException rollbackFailure) {
                log.error("Failed to roll back orphaned Stripe Coupon {} after PromotionCode creation failed",
                        stripeCoupon.getId(), rollbackFailure);
            }
            throw e;
        }

        Coupon coupon = new Coupon();
        coupon.setCode(code);
        coupon.setDiscountType(request.getDiscountType());
        coupon.setPercentOff(request.getDiscountType() == DiscountType.PERCENT
                ? BigDecimal.valueOf(request.getPercentOff()) : null);
        coupon.setAmountOffCents(request.getDiscountType() == DiscountType.FIXED_AMOUNT
                ? request.getAmountOffCents() : null);
        coupon.setDuration(duration);
        coupon.setDurationInMonths(durationInMonths);
        coupon.setExpiresAt(request.getExpiresAt());
        coupon.setMaxRedemptions(request.getMaxRedemptions());
        coupon.setStatus(CouponStatus.ACTIVE);
        coupon.setStripeCouponId(stripeCoupon.getId());
        coupon.setStripePromotionCodeId(promotionCode.getId());
        coupon.setCreatedBy(createdByUserId);

        Coupon saved = couponRepository.save(coupon);
        log.info("Created coupon {} ({}) — Stripe coupon {}, promotion code {}",
                saved.getCouponId(), code, stripeCoupon.getId(), promotionCode.getId());
        return saved;
    }

    @Override
    @Transactional
    public List<Coupon> getAllCoupons(boolean includeArchived) throws StripeException {
        List<Coupon> coupons = includeArchived
                ? couponRepository.findAll()
                : couponRepository.findAllByStatusNot(CouponStatus.ARCHIVED);
        resyncRedemptionCounts(coupons);
        return coupons;
    }

    @Override
    public Coupon getCouponByCouponId(String couponId) {
        return couponRepository.findByCouponId(couponId)
                .orElseThrow(() -> new CouponNotFoundException(couponId));
    }

    @Override
    @Transactional
    public Coupon updateCoupon(String couponId, CouponRequestDTO request) throws StripeException {
        Coupon coupon = getCouponByCouponId(couponId);
        if (coupon.getStatus() == CouponStatus.ARCHIVED) {
            throw new IllegalArgumentException("Cannot edit an archived coupon " + couponId + ".");
        }
        // Only `active` is genuinely editable at Stripe's own API level — see D5 in
        // P3-PROGRESS.md. expiresAt/maxRedemptions from the request are deliberately ignored.
        if (request.getActive() != null) {
            PromotionCode.retrieve(coupon.getStripePromotionCodeId())
                    .update(PromotionCodeUpdateParams.builder()
                            .setActive(request.getActive())
                            .build());
            coupon.setStatus(request.getActive() ? CouponStatus.ACTIVE : CouponStatus.INACTIVE);
        }
        return couponRepository.save(coupon);
    }

    @Override
    @Transactional
    public void archiveCoupon(String couponId) throws StripeException {
        Coupon coupon = getCouponByCouponId(couponId);
        if (coupon.getStatus() == CouponStatus.ARCHIVED) {
            return;
        }
        PromotionCode.retrieve(coupon.getStripePromotionCodeId())
                .update(PromotionCodeUpdateParams.builder().setActive(false).build());
        coupon.setStatus(CouponStatus.ARCHIVED);
        couponRepository.save(coupon);
        log.info("Archived coupon {} ({})", coupon.getCouponId(), coupon.getCode());
    }

    @Override
    public CouponValidateResponseDTO validateCoupon(String code, long subtotalCents) {
        if (code == null || code.isBlank()) {
            return CouponValidateResponseDTO.invalid("invalid");
        }
        // Normalized the same way as create (brief §4.8.4) — findByCodeIgnoreCase is also
        // case-insensitive at the DB level, but normalizing here too keeps the behavior
        // explicit rather than resting on that alone.
        Optional<Coupon> found = couponRepository.findByCodeIgnoreCase(code.trim().toUpperCase());
        if (found.isEmpty() || found.get().getStatus() != CouponStatus.ACTIVE) {
            return CouponValidateResponseDTO.invalid("invalid");
        }

        Coupon coupon = found.get();
        if (coupon.getExpiresAt() != null && coupon.getExpiresAt().isBefore(LocalDateTime.now())) {
            return CouponValidateResponseDTO.invalid("expired");
        }
        // Checked against the last-synced count, which can be briefly stale relative to
        // Stripe — the real enforcement point is subscription creation (brief §4.6.5).
        if (coupon.getMaxRedemptions() != null && coupon.getTimesRedeemed() >= coupon.getMaxRedemptions()) {
            return CouponValidateResponseDTO.invalid("exhausted");
        }

        long discountCents = computeDiscountCents(coupon, subtotalCents);
        long previewTotalCents = Math.max(0, subtotalCents - discountCents);
        return CouponValidateResponseDTO.valid(discountCents, previewTotalCents);
    }

    private long computeDiscountCents(Coupon coupon, long subtotalCents) {
        if (coupon.getDiscountType() == DiscountType.PERCENT) {
            return BigDecimal.valueOf(subtotalCents)
                    .multiply(coupon.getPercentOff())
                    .divide(ONE_HUNDRED, 0, RoundingMode.HALF_UP)
                    .longValueExact();
        }
        // Clamped to the subtotal — a fixed-amount coupon larger than the total is allowed
        // (brief §4.8.1) but the preview must not show a negative discount.
        return Math.min(coupon.getAmountOffCents(), subtotalCents);
    }

    private String normalizeCode(String rawCode) {
        if (rawCode == null) {
            throw new IllegalArgumentException("code is required.");
        }
        String code = rawCode.trim().toUpperCase();
        if (code.length() < CODE_MIN_LENGTH || code.length() > CODE_MAX_LENGTH
                || !CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException(
                    "code must be 3-40 characters, alphanumeric plus hyphen/underscore only.");
        }
        return code;
    }

    private void validateDiscountShape(DiscountType type, Double percentOff, Long amountOffCents) {
        if (type == null) {
            throw new IllegalArgumentException("discountType is required.");
        }
        if (type == DiscountType.PERCENT) {
            if (percentOff == null || percentOff < 1 || percentOff > 100) {
                throw new IllegalArgumentException("percentOff must be between 1 and 100 for a PERCENT coupon.");
            }
        } else if (amountOffCents == null || amountOffCents <= 0) {
            throw new IllegalArgumentException("amountOffCents must be positive for a FIXED_AMOUNT coupon.");
        }
    }

    /** Routes a REPEATING coupon with N=1 to ONCE — the two are otherwise indistinguishable
     * (brief §5.3), so this avoids two ways of expressing the same offer. */
    private CouponDuration normalizeDuration(CouponDuration duration, Integer durationInMonths) {
        if (duration == null) {
            throw new IllegalArgumentException("duration is required.");
        }
        if (duration == CouponDuration.REPEATING) {
            if (durationInMonths == null) {
                throw new IllegalArgumentException("durationInMonths is required for a REPEATING coupon.");
            }
            if (durationInMonths == 1) {
                return CouponDuration.ONCE;
            }
            if (durationInMonths < 2 || durationInMonths > 12) {
                throw new IllegalArgumentException("durationInMonths must be between 2 and 12.");
            }
        }
        return duration;
    }

    private com.stripe.model.Coupon createStripeCoupon(DiscountType type, Double percentOff, Long amountOffCents,
            CouponDuration duration, Integer durationInMonths) throws StripeException {
        CouponCreateParams.Builder builder = CouponCreateParams.builder()
                .setDuration(mapDuration(duration));

        if (type == DiscountType.PERCENT) {
            builder.setPercentOff(BigDecimal.valueOf(percentOff));
        } else {
            builder.setAmountOff(amountOffCents).setCurrency("cad");
        }
        if (duration == CouponDuration.REPEATING) {
            builder.setDurationInMonths(durationInMonths.longValue());
        }
        return com.stripe.model.Coupon.create(builder.build());
    }

    private PromotionCode createStripePromotionCode(
            String stripeCouponId, String code, LocalDateTime expiresAt, Integer maxRedemptions)
            throws StripeException {
        // The brief's §6.2 snippet calls a `setCoupon(String)` directly on
        // PromotionCodeCreateParams.Builder, which does not exist on stripe-java 31.3.0 — the
        // coupon reference is a nested `Promotion` param object instead (verified via javap;
        // same class of stale-API assumption as D5).
        PromotionCodeCreateParams.Promotion promotion = PromotionCodeCreateParams.Promotion.builder()
                .setCoupon(stripeCouponId)
                .setType(PromotionCodeCreateParams.Promotion.Type.COUPON)
                .build();
        PromotionCodeCreateParams.Builder builder = PromotionCodeCreateParams.builder()
                .setPromotion(promotion)
                .setCode(code);
        if (expiresAt != null) {
            builder.setExpiresAt(expiresAt.toEpochSecond(ZoneOffset.UTC));
        }
        if (maxRedemptions != null) {
            builder.setMaxRedemptions(maxRedemptions.longValue());
        }
        return PromotionCode.create(builder.build());
    }

    private static CouponCreateParams.Duration mapDuration(CouponDuration duration) {
        return switch (duration) {
            case ONCE -> CouponCreateParams.Duration.ONCE;
            case REPEATING -> CouponCreateParams.Duration.REPEATING;
            case FOREVER -> CouponCreateParams.Duration.FOREVER;
        };
    }

    /**
     * Refreshes {@code timesRedeemed} from Stripe for every non-archived row in one paginated
     * {@code PromotionCode.list()} call rather than one retrieve per coupon (brief §4.5).
     * Archived rows are frozen at archive time and never resynced, even if present in the
     * input list.
     */
    private void resyncRedemptionCounts(List<Coupon> coupons) throws StripeException {
        List<Coupon> toSync = coupons.stream()
                .filter(c -> c.getStatus() != CouponStatus.ARCHIVED)
                .toList();
        if (toSync.isEmpty()) {
            return;
        }

        Map<String, Long> timesRedeemedByPromotionCodeId = new HashMap<>();
        PromotionCodeCollection page = PromotionCode.list(
                PromotionCodeListParams.builder().setLimit(RESYNC_PAGE_LIMIT).build());
        for (PromotionCode promotionCode : page.getData()) {
            timesRedeemedByPromotionCodeId.put(promotionCode.getId(), promotionCode.getTimesRedeemed());
        }

        for (Coupon coupon : toSync) {
            Long timesRedeemed = timesRedeemedByPromotionCodeId.get(coupon.getStripePromotionCodeId());
            if (timesRedeemed != null && timesRedeemed.intValue() != coupon.getTimesRedeemed()) {
                coupon.setTimesRedeemed(timesRedeemed.intValue());
                couponRepository.save(coupon);
            }
        }
    }
}
