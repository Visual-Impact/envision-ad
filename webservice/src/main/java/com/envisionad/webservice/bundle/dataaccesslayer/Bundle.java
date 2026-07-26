package com.envisionad.webservice.bundle.dataaccesslayer;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.Checks;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * A rule-based grouping of screens sold as a monthly subscription. Membership is
 * computed live from {@link #ruleType}/{@link #ruleValue} rather than materialized,
 * so a media that starts matching the rule joins the candidate set on the next read.
 */
@Entity
@Table(name = "bundles")
@Checks({
        @Check(name = "chk_bundles_rule_value", constraints =
                "(rule_type = 'FULL_NETWORK' AND rule_value IS NULL)" +
                " OR (rule_type <> 'FULL_NETWORK' AND rule_value IS NOT NULL)"),
        @Check(name = "chk_bundles_discount_percent", constraints =
                "discount_percent >= 0 AND discount_percent <= 100")
})
@Data
@NoArgsConstructor
public class Bundle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bundle_id", unique = true, nullable = false, length = 36)
    private String bundleId;

    @Column(name = "name_en", nullable = false)
    private String nameEn;

    @Column(name = "name_fr", nullable = false)
    private String nameFr;

    @Column(name = "description_en", columnDefinition = "TEXT")
    private String descriptionEn;

    @Column(name = "description_fr", columnDefinition = "TEXT")
    private String descriptionFr;

    @Column(name = "ideal_for_en", length = 500)
    private String idealForEn;

    @Column(name = "ideal_for_fr", length = 500)
    private String idealForFr;

    /** Hex color, same convention as venue.color_code. */
    @Column(name = "badge_color", nullable = false, length = 7)
    private String badgeColor;

    @Column(name = "rule_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private BundleRuleType ruleType;

    /** City name / region name / venue.venue_id. Null exactly when ruleType is FULL_NETWORK. */
    @Column(name = "rule_value")
    private String ruleValue;

    /**
     * Admin can deactivate without deleting: hides the bundle from discovery while
     * leaving existing subscriptions untouched.
     */
    @Column(name = "active", nullable = false)
    private boolean active = true;

    /**
     * Whole-percent discount applied to the summed screen price by
     * {@code BundleDiscountModifier}. 0 = no discount.
     *
     * <p>The discount reduces what the advertiser is charged
     * ({@code bundle_subscriptions.monthly_amount}) but NOT what media owners are
     * paid — {@code bundle_subscription_items} keep each screen's full
     * {@code media.price}, so the platform absorbs it out of its own fee.
     */
    @Column(name = "discount_percent", nullable = false)
    private int discountPercent = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    private void generateBundleId() {
        if (this.bundleId == null) {
            this.bundleId = java.util.UUID.randomUUID().toString();
        }
    }
}
