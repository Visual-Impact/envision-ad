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

    /** Batch load of several subscriptions' frozen screen lists (P12's dashboards). */
    List<BundleSubscriptionItem> findAllBySubscriptionIdIn(Collection<String> subscriptionIds);

    /**
     * The media-owner businesses whose screens a subscription covers (P12). One element means a
     * single-owner subscription, the only kind that can carry a proof-of-display link in v1.
     * Fixed for the subscription's life, because its items are frozen at checkout.
     */
    @Query("""
            SELECT DISTINCT i.mediaOwnerBusinessId
            FROM BundleSubscriptionItem i
            WHERE i.subscriptionId = :subscriptionId
            """)
    List<String> findDistinctMediaOwnerBusinessIdsBySubscriptionId(
            @Param("subscriptionId") String subscriptionId);

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

    /**
     * How many distinct screens of one venue an advertiser is paying for across their live
     * subscriptions. Drives the admin warning before a business type is set or changed (P4): those
     * screens stay in the frozen {@code bundle_subscription_items}, so the advertiser keeps paying
     * for them, while P6's distribution stops sending their creatives there.
     */
    @Query("""
            SELECT COUNT(DISTINCT i.mediaId)
            FROM BundleSubscriptionItem i, BundleSubscription s, Media m
            WHERE i.subscriptionId = s.subscriptionId
              AND m.id = i.mediaId
              AND s.advertiserBusinessId = :businessId
              AND m.venueId = :venueId
              AND s.status IN :statuses
            """)
    long countDistinctMediaInVenueForAdvertiser(
            @Param("businessId") String businessId,
            @Param("venueId") String venueId,
            @Param("statuses") Collection<BundleSubscriptionStatus> statuses);
}
