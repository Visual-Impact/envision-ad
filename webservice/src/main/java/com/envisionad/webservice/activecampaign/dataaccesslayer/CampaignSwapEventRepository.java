package com.envisionad.webservice.activecampaign.dataaccesslayer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface CampaignSwapEventRepository extends JpaRepository<CampaignSwapEvent, Long> {

    Optional<CampaignSwapEvent> findTopByBusinessIdAndEventTypeInOrderByTriggeredAtDesc(
            String businessId, Collection<CampaignSwapEventType> eventTypes);

    Optional<CampaignSwapEvent> findTopByToCampaignIdAndEventTypeInOrderByTriggeredAtDesc(
            String campaignId, Collection<CampaignSwapEventType> eventTypes);
}
