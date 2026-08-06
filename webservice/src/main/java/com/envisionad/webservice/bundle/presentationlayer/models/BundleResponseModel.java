package com.envisionad.webservice.bundle.presentationlayer.models;

import com.envisionad.webservice.bundle.dataaccesslayer.BundleRuleType;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class BundleResponseModel {
    private String bundleId;
    private String nameEn;
    private String nameFr;
    private String descriptionEn;
    private String descriptionFr;
    private String idealForEn;
    private String idealForFr;
    private String badgeColor;
    private BundleRuleType ruleType;
    private String ruleValue;

    /**
     * Human-readable rendering of {@link #ruleValue}, so no client has to know that
     * the column means a different thing per {@link #ruleType}: the venue's name for
     * VENUE, the raw city/region string for CITY/REGION, null for FULL_NETWORK.
     *
     * <p>Response-only and derived — {@code BundleRequestModel} still writes
     * {@link #ruleValue} alone. Shipped as an En/Fr pair rather than resolved from a
     * {@code locale} param so the public listing stays locale-free, matching how
     * {@link #nameEn}/{@link #nameFr} already let the client pick.
     *
     * <p>Falls back to the raw {@link #ruleValue} when a VENUE bundle points at a
     * venue row that no longer exists, which keeps the broken reference visible
     * instead of blanking the cell.
     */
    private String ruleValueLabelEn;
    private String ruleValueLabelFr;

    private boolean active;

    /** Eligible screens after the pricing pipeline's filter chain. */
    private int screenCount;

    /** Sum of the eligible screens' monthly prices, before any discount. */
    private BigDecimal basePrice;

    /**
     * What the advertiser actually pays: {@link #basePrice} after the bundle's
     * discount. Equal to basePrice when {@link #discountPercent} is 0.
     */
    private BigDecimal finalPrice;

    /** Whole-percent discount on this bundle; 0 when none. */
    private int discountPercent;

    /**
     * The shared per-screen price when every eligible screen has the same non-null
     * price; null for an empty, mixed-price, or partially-priceless set. Drives the
     * discovery card's "$X × N screens" vs. "N screens" subline.
     */
    private BigDecimal perScreenPrice;

    /**
     * {@link #perScreenPrice} after the discount, or null when there is no discount
     * or no honest per-screen figure. Drives the card's per-screen comparison.
     */
    private BigDecimal discountedPerScreenPrice;

    /**
     * Subscriptions in INCOMPLETE/ACTIVE/PAST_DUE. Non-zero means delete is blocked;
     * the admin delete modal warns on this before the request is even sent, the same
     * way the venue delete modal uses mediaCount.
     */
    private long activeSubscriptionCount;
}
