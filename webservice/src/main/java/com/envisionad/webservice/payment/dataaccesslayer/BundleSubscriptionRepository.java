package com.envisionad.webservice.payment.dataaccesslayer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BundleSubscriptionRepository extends JpaRepository<BundleSubscription, Long> {

    Optional<BundleSubscription> findBySubscriptionId(String subscriptionId);

    Optional<BundleSubscription> findByStripeCheckoutSessionId(String stripeCheckoutSessionId);

    Optional<BundleSubscription> findByStripeSubscriptionId(String stripeSubscriptionId);

    List<BundleSubscription> findAllByAdvertiserBusinessId(String advertiserBusinessId);

    List<BundleSubscription> findAllByBundleIdAndStatusIn(
            String bundleId, Collection<BundleSubscriptionStatus> statuses);

    /**
     * The subscribe path's duplicate guard and INCOMPLETE-reuse lookup. Enforced here at
     * the service layer because the partial unique index backing this rule is Flyway-only
     * and absent from the entity-generated test schema.
     */
    Optional<BundleSubscription> findByBundleIdAndAdvertiserBusinessIdAndStatusIn(
            String bundleId, String advertiserBusinessId, Collection<BundleSubscriptionStatus> statuses);

    long countByBundleIdAndStatusIn(
            String bundleId, Collection<BundleSubscriptionStatus> statuses);

    /**
     * The campaign-delete guard (M6, decision D42). Mirrors the {@code bundle_subscriptions}
     * clause of the {@code prevent_active_campaign_delete()} trigger so the app layer returns a
     * clean 409 before the database raises a constraint violation.
     */
    boolean existsByCampaignIdAndStatusIn(
            String campaignId, Collection<BundleSubscriptionStatus> statuses);

    /**
     * Advertiser dashboard's "active campaigns" tile. DISTINCT matters: one campaign can back
     * several subscriptions (an advertiser may hold several bundles on the same campaign), and the
     * tile counts campaigns, not subscriptions.
     */
    @Query("""
            SELECT COUNT(DISTINCT s.campaignId)
            FROM BundleSubscription s
            WHERE s.advertiserBusinessId = :advertiserBusinessId
              AND s.status IN :statuses
            """)
    int countDistinctCampaignsByAdvertiserBusinessIdAndStatusIn(
            @Param("advertiserBusinessId") String advertiserBusinessId,
            @Param("statuses") Collection<BundleSubscriptionStatus> statuses);

    /**
     * Advertiser spend metric. "Booking basis" is preserved from the reservation era: a
     * subscription counts toward the period in which it was created, not every period it renews in.
     */
    List<BundleSubscription> findAllByAdvertiserBusinessIdAndCreatedAtBetween(
            String advertiserBusinessId, LocalDateTime start, LocalDateTime end);
}
