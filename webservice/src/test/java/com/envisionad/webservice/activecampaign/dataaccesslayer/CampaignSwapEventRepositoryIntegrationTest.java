package com.envisionad.webservice.activecampaign.dataaccesslayer;

import com.envisionad.webservice.config.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CampaignSwapEventRepositoryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CampaignSwapEventRepository repository;

    @BeforeEach
    void cleanUp() {
        repository.deleteAll();
    }

    @Test
    void saveAndFindLatestBusinessEvent_preservesAuditFields() {
        CampaignSwapEvent older = event("business-1", "campaign-a", CampaignSwapEventType.SWAP);
        repository.saveAndFlush(older);

        CampaignSwapEvent latest = event(
                "business-1", "campaign-a", CampaignSwapEventType.MANUAL_NOTIFY);
        latest.setFromCampaignId("campaign-a");
        latest.setTriggeredByUserId("auth0|user-1");
        latest.setRecipientsNotified(3);
        latest.setRecipientsFailed(1);
        repository.saveAndFlush(latest);

        CampaignSwapEvent result = repository
                .findTopByBusinessIdAndEventTypeInOrderByTriggeredAtDesc(
                        "business-1",
                        List.of(CampaignSwapEventType.SWAP, CampaignSwapEventType.MANUAL_NOTIFY))
                .orElseThrow();

        assertEquals(CampaignSwapEventType.MANUAL_NOTIFY, result.getEventType());
        assertEquals("campaign-a", result.getFromCampaignId());
        assertEquals("campaign-a", result.getToCampaignId());
        assertEquals("auth0|user-1", result.getTriggeredByUserId());
        assertEquals(3, result.getRecipientsNotified());
        assertEquals(1, result.getRecipientsFailed());
        assertNotNull(result.getTriggeredAt());
    }

    @Test
    void findLatestCampaignNotification_ignoresInitialSelection() {
        repository.saveAndFlush(event(
                "business-1", "campaign-a", CampaignSwapEventType.MANUAL_NOTIFY));
        repository.saveAndFlush(event(
                "business-1", "campaign-a", CampaignSwapEventType.INITIAL_SELECTION));

        CampaignSwapEvent result = repository
                .findTopByToCampaignIdAndEventTypeInOrderByTriggeredAtDesc(
                        "campaign-a",
                        List.of(CampaignSwapEventType.SWAP,
                                CampaignSwapEventType.MANUAL_NOTIFY,
                                CampaignSwapEventType.AUTO_NOTIFY))
                .orElseThrow();

        assertEquals(CampaignSwapEventType.MANUAL_NOTIFY, result.getEventType());
    }

    private static CampaignSwapEvent event(
            String businessId, String campaignId, CampaignSwapEventType type) {
        CampaignSwapEvent event = new CampaignSwapEvent();
        event.setBusinessId(businessId);
        event.setToCampaignId(campaignId);
        event.setEventType(type);
        return event;
    }
}
