package com.envisionad.webservice.business.dataaccesslayer;

import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface BusinessRepository extends JpaRepository<Business, String> {
    Business findByBusinessId_BusinessId(String businessId);
    List<Business> findAllByBusinessId_BusinessIdIn(List<String> businessIds);
    boolean existsByNameAndBusinessId_BusinessIdNot(String Name, String businessId);
    boolean existsByBusinessId_BusinessId(String businessId);

    /**
     * Candidates for P6's automatic notification sweep (FR-8.5): advertisers whose currently
     * displayed campaign had creatives added or removed longer ago than the quiet period, and
     * who are actually paying for screens right now.
     *
     * <p>The live-subscription clause is not an optimisation — FR-8.5 requires that businesses
     * with no live subscription are skipped entirely, because there are no affected owners and
     * the sweep must not manufacture an event row for a notification nobody needed.
     *
     * <p>This deliberately does <em>not</em> check whether the changes were already notified.
     * That question is per-campaign and needs the latest event of three types, which is a
     * different shape of query; the caller settles it with
     * {@code CampaignSwapEventRepository.findTopByToCampaignId...}. Narrowing on the indexed
     * {@code creatives_updated_at} first keeps that follow-up to the handful of rows that could
     * possibly qualify, and in the common case to none at all.
     */
    @Query("""
            SELECT b FROM Business b, AdCampaign c
            WHERE b.activeCampaignId = c.campaignId.campaignId
              AND c.creativesUpdatedAt IS NOT NULL
              AND c.creativesUpdatedAt < :changedBefore
              AND EXISTS (SELECT 1 FROM BundleSubscription s
                          WHERE s.advertiserBusinessId = b.businessId.businessId
                            AND s.status IN :liveStatuses)
            """)
    List<Business> findBusinessesWithCreativeChangesOlderThan(
            @Param("changedBefore") LocalDateTime changedBefore,
            @Param("liveStatuses") Collection<BundleSubscriptionStatus> liveStatuses);
}
