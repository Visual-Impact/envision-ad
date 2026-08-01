package com.envisionad.webservice.payment.presentationlayer.models;

import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionStatus;
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
}
