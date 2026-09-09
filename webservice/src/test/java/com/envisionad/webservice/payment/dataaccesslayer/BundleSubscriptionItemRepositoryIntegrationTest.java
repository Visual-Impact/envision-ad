package com.envisionad.webservice.payment.dataaccesslayer;

import com.envisionad.webservice.business.dataaccesslayer.Business;
import com.envisionad.webservice.business.dataaccesslayer.BusinessIdentifier;
import com.envisionad.webservice.business.dataaccesslayer.BusinessRepository;
import com.envisionad.webservice.config.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionStatus.ACTIVE;
import static com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionStatus.CANCELED;
import static com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionStatus.PAST_DUE;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The two proof-of-display readers on {@link BundleSubscriptionItemRepository}, after the P6
 * follow-up dropped {@code bundle_subscriptions.campaign_id}. Both now derive "what runs on the
 * screen" from {@code business.active_campaign_id} via a three-way JPQL join, so they need a
 * real Postgres (the join and the embedded-id path are the risky part).
 */
class BundleSubscriptionItemRepositoryIntegrationTest extends BaseIntegrationTest {

    private static final List<BundleSubscriptionStatus> LIVE = List.of(ACTIVE, PAST_DUE);

    private static final String ADVERTISER_BIZ = "advertiser-biz-1";
    private static final String CAMPAIGN_ON_SCREEN = "campaign-active-1";
    private static final String CAMPAIGN_SWAPPED_AWAY = "campaign-old-1";

    @Autowired private BundleSubscriptionRepository subscriptionRepository;
    @Autowired private BundleSubscriptionItemRepository itemRepository;
    @Autowired private BusinessRepository businessRepository;

    private final UUID mediaId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        itemRepository.deleteAll();
        subscriptionRepository.deleteAll();
        businessRepository.deleteAll();
    }

    private void givenAdvertiserActiveCampaign(String activeCampaignId) {
        Business business = new Business();
        business.setBusinessId(new BusinessIdentifier(ADVERTISER_BIZ));
        business.setName("Advertiser");
        business.setActiveCampaignId(activeCampaignId);
        businessRepository.save(business);
    }

    /** Returns the new subscription's id. */
    private String givenSubscriptionCoveringTheScreen(BundleSubscriptionStatus status) {
        BundleSubscription subscription = new BundleSubscription();
        String subscriptionId = UUID.randomUUID().toString();
        subscription.setSubscriptionId(subscriptionId);
        subscription.setBundleId(UUID.randomUUID().toString());
        subscription.setAdvertiserBusinessId(ADVERTISER_BIZ);
        subscription.setStripeCheckoutSessionId("cs_" + UUID.randomUUID());
        subscription.setStatus(status);
        subscription.setMonthlyAmount(new BigDecimal("10.00"));
        subscription.setScreenCount(1);
        subscriptionRepository.save(subscription);

        BundleSubscriptionItem item = new BundleSubscriptionItem();
        item.setSubscriptionId(subscriptionId);
        item.setMediaId(mediaId);
        item.setMediaOwnerBusinessId("owner-biz-1");
        item.setMonthlyAmount(new BigDecimal("10.00"));
        itemRepository.save(item);
        return subscriptionId;
    }

    @Test
    void existsForMediaAndCampaign_trueWhenTheScreensLiveSubscriptionAdvertiserActiveCampaignMatches() {
        givenAdvertiserActiveCampaign(CAMPAIGN_ON_SCREEN);
        givenSubscriptionCoveringTheScreen(ACTIVE);

        assertTrue(itemRepository.existsForMediaAndCampaignWithSubscriptionStatusIn(
                mediaId, CAMPAIGN_ON_SCREEN, LIVE));
    }

    @Test
    void existsForMediaAndCampaign_falseForACampaignThatHasBeenSwappedAwayFrom() {
        // Pointer moved to CAMPAIGN_ON_SCREEN; proof for the old campaign must now be rejected.
        givenAdvertiserActiveCampaign(CAMPAIGN_ON_SCREEN);
        givenSubscriptionCoveringTheScreen(ACTIVE);

        assertFalse(itemRepository.existsForMediaAndCampaignWithSubscriptionStatusIn(
                mediaId, CAMPAIGN_SWAPPED_AWAY, LIVE));
    }

    @Test
    void existsForMediaAndCampaign_falseWhenTheOnlySubscriptionCoveringTheScreenIsNotLive() {
        givenAdvertiserActiveCampaign(CAMPAIGN_ON_SCREEN);
        givenSubscriptionCoveringTheScreen(CANCELED);

        assertFalse(itemRepository.existsForMediaAndCampaignWithSubscriptionStatusIn(
                mediaId, CAMPAIGN_ON_SCREEN, LIVE));
    }

    @Test
    void findLiveCampaignIds_returnsTheAdvertiserActiveCampaignOnce_evenAcrossTwoSubscriptions() {
        givenAdvertiserActiveCampaign(CAMPAIGN_ON_SCREEN);
        givenSubscriptionCoveringTheScreen(ACTIVE);
        givenSubscriptionCoveringTheScreen(PAST_DUE);

        assertEquals(List.of(CAMPAIGN_ON_SCREEN),
                itemRepository.findLiveCampaignIdsByMediaId(mediaId, LIVE));
    }

    @Test
    void findLiveCampaignIds_skipsAnAdvertiserWhoHasNotPickedACampaignYet() {
        givenAdvertiserActiveCampaign(null);
        givenSubscriptionCoveringTheScreen(ACTIVE);

        assertTrue(itemRepository.findLiveCampaignIdsByMediaId(mediaId, LIVE).isEmpty(),
                "a null active_campaign_id must not surface as a (null, null) picker row");
    }
}
