package com.envisionad.webservice.payment.presentationlayer.models;

import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionStatus;
import com.envisionad.webservice.payment.dataaccesslayer.CouponDuration;
import com.envisionad.webservice.payment.dataaccesslayer.DiscountType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One row of the advertiser's subscription list (P1 M6).
 * <p>
 * Carries both locales of the bundle name rather than a resolved string, matching
 * {@code BundleResponseModel} — bundle content is admin-entered bilingual data, so the client
 * picks the locale, exactly as it already does on the discovery cards.
 */
@Data
public class BundleSubscriptionResponseModel {

    private String subscriptionId;
    private String bundleId;
    private String bundleNameEn;
    private String bundleNameFr;

    private BundleSubscriptionStatus status;

    /** The locked monthly price. Never recomputed — see MASTER-BRIEF §5.4. */
    private BigDecimal monthlyAmount;

    /** Screen count frozen at signup; today's bundle may well contain more. */
    private Integer screenCount;

    /**
     * Next renewal date, or {@code null}.
     * <p>
     * NULL is a legitimate state on an ACTIVE subscription, not a data fault:
     * {@code checkout.session.completed} activates a row without a renewal date and only
     * {@code invoice.paid} sets one. The UI must render the absence rather than assume a value.
     */
    private LocalDateTime currentPeriodEnd;

    /**
     * True when the advertiser has cancelled but the paid-for period has not elapsed. Such a row
     * is still ACTIVE — the client has to distinguish this from a plain ACTIVE one, because the
     * difference is "renews on X" versus "ends on X".
     */
    private boolean cancelAtPeriodEnd;

    private LocalDateTime canceledAt;
    private LocalDateTime createdAt;

    /** The campaign running on this subscription's screens — P6's active-campaign slot. */
    private String campaignId;
    private String campaignName;

    /**
     * The coupon applied at signup, if any (P3) — all null when none was. Deliberately just the
     * coupon's static terms, never a computed current-cycle price: {@link #monthlyAmount} stays
     * the locked pre-coupon figure (must never reflect the coupon — see MASTER-BRIEF §5.4/D30),
     * so the client shows both and lets the advertiser read the terms themselves rather than
     * trusting a number we'd have to independently reconstruct Stripe's billing-cycle state to
     * compute correctly.
     */
    private String couponCode;
    private DiscountType couponDiscountType;
    private BigDecimal couponPercentOff;
    private Long couponAmountOffCents;
    private CouponDuration couponDuration;
    private Integer couponDurationInMonths;
}
