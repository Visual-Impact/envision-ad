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

    /**
     * The Advertiser-role-removal guard (admin role-change endpoint): a business with
     * any live subscription still bills it, so the role can't be dropped without first
     * cancelling. Mirrors {@link #countByBundleIdAndStatusIn}'s status-scoping.
     */
    long countByAdvertiserBusinessIdAndStatusIn(String advertiserBusinessId, Collection<BundleSubscriptionStatus> statuses);

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
     * The bundle-delete guard (M6, decision D47). Status-agnostic for the same reason as
     * {@link #existsByCampaignId}: {@code bundle_subscriptions.bundle_id} carries no
     * {@code ON DELETE} clause, so Postgres defaults to NO ACTION and refuses to delete a bundle
     * that any subscription still references. The brief's req. 5 and this service's own comment
     * both claimed canceled-only history cascaded away — verified false against a real migrated
     * schema.
     */
    long countByBundleId(String bundleId);

    /**
     * The campaign-delete guard (M6, decisions D42 and D47).
     * <p>
     * Deliberately status-agnostic, because it mirrors the <em>foreign key</em> rather than the
     * trigger. {@code bundle_subscriptions.campaign_id} is {@code ON DELETE RESTRICT}, so a
     * campaign referenced by any row — CANCELED and INCOMPLETE included — cannot be deleted at
     * all. Scoping this to live statuses (as it briefly did) let such a delete past the service
     * only to die at the constraint, surfacing as the catch-all's generic "conflicting database
     * state" 409 instead of an explanation.
     */
    boolean existsByCampaignId(String campaignId);

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
