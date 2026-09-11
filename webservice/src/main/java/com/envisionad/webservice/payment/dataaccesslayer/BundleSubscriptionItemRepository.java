package com.envisionad.webservice.payment.dataaccesslayer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface BundleSubscriptionItemRepository extends JpaRepository<BundleSubscriptionItem, Long> {

    List<BundleSubscriptionItem> findAllBySubscriptionId(String subscriptionId);

    /** Clears a retried checkout's stale split before the fresh one is written. */
    void deleteAllBySubscriptionId(String subscriptionId);

    /**
     * The proof-of-display gate (M6, decision D40): is this campaign actually running on this
     * screen? True when the media is locked into a live (one of {@code statuses}) subscription
     * whose advertiser's current active campaign is {@code campaignId}.
     * <p>
     * Since the P6 follow-up dropped {@code bundle_subscriptions.campaign_id}, "what runs on the
     * screen" is derived from the single source of truth — {@code business.active_campaign_id} —
     * rather than a value frozen at checkout. Joins on scalar FK columns rather than mapped
     * associations because these entities use plain scalar FKs throughout (decision D5);
     * {@code Business.businessId} is an embedded identifier, hence the {@code .businessId} path.
     */
    @Query("""
            SELECT (COUNT(i) > 0)
            FROM BundleSubscriptionItem i, BundleSubscription s, Business b
            WHERE i.subscriptionId = s.subscriptionId
              AND b.businessId.businessId = s.advertiserBusinessId
              AND i.mediaId = :mediaId
              AND b.activeCampaignId = :campaignId
              AND s.status IN :statuses
            """)
    boolean existsForMediaAndCampaignWithSubscriptionStatusIn(
            @Param("mediaId") UUID mediaId,
            @Param("campaignId") String campaignId,
            @Param("statuses") Collection<BundleSubscriptionStatus> statuses);

    /**
     * "Which campaigns are live on this screen" — drives the proof-of-display campaign picker,
     * which previously derived the same list client-side from that media's reservations.
     * <p>
     * After the P6 follow-up this resolves to the distinct set of advertiser active campaigns
     * covering the screen. {@code b.activeCampaignId IS NOT NULL} is required, not defensive: the
     * pointer is legitimately null for an advertiser who has a live subscription but has not
     * picked a campaign yet, and a null element would flow into the picker as a {@code (null,
     * null)} row. DISTINCT because one campaign can reach the same screen through more than one
     * of that advertiser's subscriptions.
     */
    @Query("""
            SELECT DISTINCT b.activeCampaignId
            FROM BundleSubscriptionItem i, BundleSubscription s, Business b
            WHERE i.subscriptionId = s.subscriptionId
              AND b.businessId.businessId = s.advertiserBusinessId
              AND i.mediaId = :mediaId
              AND s.status IN :statuses
              AND b.activeCampaignId IS NOT NULL
            """)
    List<String> findLiveCampaignIdsByMediaId(
            @Param("mediaId") UUID mediaId,
            @Param("statuses") Collection<BundleSubscriptionStatus> statuses);

    /**
     * Every screen an advertiser is currently paying to appear on, across all of their live
     * subscriptions — the input to P6's "who has to be told about a campaign swap" question.
     * <p>
     * Deliberately read from the frozen {@code bundle_subscription_items} rows rather than
     * re-evaluating the bundles' rules through {@code BundleService.getRuleMatchedMedias}. Those
     * two answers drift apart on purpose: items are locked at checkout (see
     * {@link BundleSubscriptionItem}'s javadoc) so that payouts reflect what was actually bought,
     * while rule matching reflects the network as it stands today. Notifying on rule matches
     * would mail an owner whose screen has newly started matching a bundle the advertiser is not
     * paying for it under, and skip an owner whose paid-for screen has since stopped matching.
     * <p>
     * DISTINCT because one screen can reach the same advertiser through more than one bundle.
     */
    @Query("""
            SELECT DISTINCT i.mediaId
            FROM BundleSubscriptionItem i, BundleSubscription s
            WHERE i.subscriptionId = s.subscriptionId
              AND s.advertiserBusinessId = :businessId
              AND s.status IN :statuses
            """)
    List<UUID> findDistinctMediaIdsByAdvertiserBusinessId(
            @Param("businessId") String businessId,
            @Param("statuses") Collection<BundleSubscriptionStatus> statuses);

    /**
     * The Media-Owner-role-removal guard (admin role-change endpoint): true when at
     * least one of this business's screens is locked into a live subscription, via the
     * denormalized {@code mediaOwnerBusinessId} (same id space as {@code business.business_id},
     * see that field's own javadoc) rather than joining through {@code Media} — the
     * subscription may belong to a different business than the one losing the role,
     * which is exactly the case this guard exists to catch.
     */
    @Query("""
            SELECT (COUNT(i) > 0)
            FROM BundleSubscriptionItem i, BundleSubscription s
            WHERE i.subscriptionId = s.subscriptionId
              AND i.mediaOwnerBusinessId = :businessId
              AND s.status IN :statuses
            """)
    boolean existsLiveSubscriptionForMediaOwnerBusinessId(
            @Param("businessId") String businessId,
            @Param("statuses") Collection<BundleSubscriptionStatus> statuses);
}
