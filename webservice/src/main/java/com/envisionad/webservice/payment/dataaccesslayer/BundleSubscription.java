package com.envisionad.webservice.payment.dataaccesslayer;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * An advertiser's monthly subscription to a bundle — the source of truth for "who
 * advertises where."
 *
 * <p>Price is locked for the life of the subscription: {@link #monthlyAmount} and
 * {@link #screenCount} are frozen at creation, and the per-owner split is frozen in
 * the matching {@link BundleSubscriptionItem} rows. Screens that start matching the
 * bundle's rule later affect only future subscribers.
 */
@Entity
@Table(name = "bundle_subscriptions")
@Data
@NoArgsConstructor
public class BundleSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subscription_id", unique = true, nullable = false, length = 36)
    private String subscriptionId;

    @Column(name = "bundle_id", nullable = false, length = 36)
    private String bundleId;

    @Column(name = "advertiser_business_id", nullable = false, length = 36)
    private String advertiserBusinessId;

    /** The active campaign hook for P6; protected by the campaign-delete trigger. */
    @Column(name = "campaign_id", nullable = false, length = 36)
    private String campaignId;

    /** Null until the checkout.session.completed webhook links it. */
    @Column(name = "stripe_subscription_id", unique = true)
    private String stripeSubscriptionId;

    @Column(name = "stripe_checkout_session_id", unique = true, nullable = false)
    private String stripeCheckoutSessionId;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private BundleSubscriptionStatus status;

    /** Locked total at creation (post-eligibility, pre-coupon). */
    @Column(name = "monthly_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal monthlyAmount;

    /** Locked eligible screen count at creation. */
    @Column(name = "screen_count", nullable = false)
    private Integer screenCount;

    /** Synced from Stripe on each invoice event. */
    @Column(name = "current_period_end")
    private LocalDateTime currentPeriodEnd;

    @Column(name = "cancel_at_period_end", nullable = false)
    private boolean cancelAtPeriodEnd = false;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    private void generateSubscriptionId() {
        if (this.subscriptionId == null) {
            this.subscriptionId = java.util.UUID.randomUUID().toString();
        }
    }
}
