package com.envisionad.webservice.payment.dataaccesslayer;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One screen's frozen share of a {@link BundleSubscription}, captured at signup.
 *
 * <p>Exists so the monthly payout job knows exactly how much to transfer to each
 * media owner without recomputing eligibility, which drifts as media are added and
 * removed.
 */
@Entity
@Table(name = "bundle_subscription_items")
@Data
@NoArgsConstructor
public class BundleSubscriptionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subscription_id", nullable = false, length = 36)
    private String subscriptionId;

    @Column(name = "media_id", nullable = false)
    private UUID mediaId;

    /**
     * Denormalized at creation time: the media could change hands later, but the
     * payout must reflect the owner at signup. Holds {@code media.businessId} as a
     * string, which is the same id space as {@code business.business_id} and
     * {@code stripe_accounts.business_id}.
     */
    @Column(name = "media_owner_business_id", nullable = false, length = 36)
    private String mediaOwnerBusinessId;

    /** This screen's media.price at lock time. */
    @Column(name = "monthly_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal monthlyAmount;
}
