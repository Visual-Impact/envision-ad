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
    private boolean active;

    /** Eligible screens after the pricing pipeline's filter chain. */
    private int screenCount;

    /** Sum of the eligible screens' monthly prices. */
    private BigDecimal basePrice;

    /**
     * The shared per-screen price when every eligible screen has the same non-null
     * price; null for an empty, mixed-price, or partially-priceless set. Drives the
     * discovery card's "$X × N screens" vs. "N screens" subline.
     */
    private BigDecimal perScreenPrice;

    /**
     * Subscriptions in INCOMPLETE/ACTIVE/PAST_DUE. Non-zero means delete is blocked;
     * the admin delete modal warns on this before the request is even sent, the same
     * way the venue delete modal uses mediaCount.
     */
    private long activeSubscriptionCount;
}
