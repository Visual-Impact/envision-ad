package com.envisionad.webservice.bundle.presentationlayer.models;

import com.envisionad.webservice.bundle.dataaccesslayer.BundleRuleType;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class BundleRequestModel {
    private String nameEn;
    private String nameFr;
    private String descriptionEn;
    private String descriptionFr;
    private String idealForEn;
    private String idealForFr;
    private String badgeColor;
    private BundleRuleType ruleType;
    /** Null when ruleType is FULL_NETWORK. */
    private String ruleValue;
    /** Optional on update; ignored on create, where bundles default to active. */
    private Boolean active;
    /** Whole-percent discount, 0–100. Null/absent is treated as 0. */
    private Integer discountPercent;
}
