package com.envisionad.webservice.advertisement.dataaccesslayer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AdCampaignRepository extends JpaRepository<AdCampaign, Integer> {
    AdCampaign findByCampaignId_CampaignId(String campaignId);

    // Fetch AdCampaign along with its associated ads. Added for integration test purposes.
    @Query("select c from AdCampaign c left join fetch c.ads where c.campaignId.campaignId = :campaignId")
    AdCampaign findByCampaignIdWithAds(@Param("campaignId") String campaignId);

    List<AdCampaign> findAllByBusinessId_BusinessId(String businessId);

    List<AdCampaign> findAllByBusinessId_BusinessIdAndArchivedAtIsNull(String businessId);

    List<AdCampaign> findAllByCampaignId_CampaignIdIn(List<String> campaignIds);
}
