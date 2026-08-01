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
     * screen? True when the campaign holds a subscription in one of {@code statuses} whose
     * locked item set includes the media.
     * <p>
     * Joins on {@code subscriptionId} rather than a mapped association because these entities use
     * plain scalar FK columns throughout (decision D5).
     */
    @Query("""
            SELECT (COUNT(i) > 0)
            FROM BundleSubscriptionItem i, BundleSubscription s
            WHERE i.subscriptionId = s.subscriptionId
              AND i.mediaId = :mediaId
              AND s.campaignId = :campaignId
              AND s.status IN :statuses
            """)
    boolean existsForMediaAndCampaignWithSubscriptionStatusIn(
            @Param("mediaId") UUID mediaId,
            @Param("campaignId") String campaignId,
            @Param("statuses") Collection<BundleSubscriptionStatus> statuses);

    /**
     * "Which campaigns are live on this screen" — drives the proof-of-display campaign picker,
     * which previously derived the same list client-side from that media's reservations. DISTINCT
     * because one campaign can reach the same screen through more than one subscription.
     */
    @Query("""
            SELECT DISTINCT s.campaignId
            FROM BundleSubscriptionItem i, BundleSubscription s
            WHERE i.subscriptionId = s.subscriptionId
              AND i.mediaId = :mediaId
              AND s.status IN :statuses
            """)
    List<String> findLiveCampaignIdsByMediaId(
            @Param("mediaId") UUID mediaId,
            @Param("statuses") Collection<BundleSubscriptionStatus> statuses);
}
