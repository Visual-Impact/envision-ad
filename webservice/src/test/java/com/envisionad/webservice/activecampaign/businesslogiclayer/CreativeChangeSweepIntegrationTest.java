package com.envisionad.webservice.activecampaign.businesslogiclayer;

import com.envisionad.webservice.activecampaign.dataaccesslayer.CampaignSwapEvent;
import com.envisionad.webservice.activecampaign.dataaccesslayer.CampaignSwapEventRepository;
import com.envisionad.webservice.activecampaign.dataaccesslayer.CampaignSwapEventType;
import com.envisionad.webservice.advertisement.dataaccesslayer.*;
import com.envisionad.webservice.business.dataaccesslayer.*;
import com.envisionad.webservice.config.BaseIntegrationTest;
import com.envisionad.webservice.payment.dataaccesslayer.*;
import com.envisionad.webservice.venue.dataaccesslayer.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FR-8's automatic backstop, against a real Postgres. The sweep is invoked directly rather than
 * waited on: its schedule is switched off in the test profile precisely so a timer cannot fire
 * into {@code BaseIntegrationTest}'s shared context, and a test that waits on wall-clock time
 * would be slow and flaky for no extra proof.
 */
public class CreativeChangeSweepIntegrationTest extends BaseIntegrationTest {

    private static final String BUSINESS_ID = "d2eebc99-9c0b-4ef8-bb6d-6bb9bd380b44";
    private static final String TEST_USER_ID = "auth0|696a88eb347945897ef17093";

    @Autowired private BusinessRepository businessRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private AdCampaignRepository adCampaignRepository;
    @Autowired private CampaignSwapEventRepository campaignSwapEventRepository;
    @Autowired private BundleSubscriptionRepository bundleSubscriptionRepository;
    @Autowired private VenueRepository venueRepository;
    @Autowired private ActiveCampaignService activeCampaignService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private BusinessIdentifier businessId;

    @BeforeEach
    void setUp() {
        campaignSwapEventRepository.deleteAll();
        bundleSubscriptionRepository.deleteAll();
        businessRepository.findAll().forEach(business -> {
            business.setActiveCampaignId(null);
            businessRepository.save(business);
        });
        adCampaignRepository.deleteAll();
        venueRepository.deleteAll();
        employeeRepository.deleteAll();
        businessRepository.deleteAll();

        businessId = new BusinessIdentifier(BUSINESS_ID);
        Business business = new Business();
        business.setBusinessId(businessId);
        business.setName("Acme Co");
        business.setOwnerId(TEST_USER_ID);
        business.setOrganizationSize(OrganizationSize.LARGE);
        business.setVerified(true);
        Address address = new Address();
        address.setCity("St-Lambert");
        address.setStreet("900 Riverside");
        address.setCountry("Canada");
        address.setState("QC");
        address.setZipCode("J4P 3P2");
        business.setAddress(address);
        Roles roles = new Roles();
        roles.setAdvertiser(true);
        business.setRoles(roles);
        businessRepository.save(business);
    }

    /** The whole point of FR-8: nobody pressed anything, and the owners still get told. */
    @Test
    void changesOlderThanTheQuietPeriod_areNotifiedWithoutAnyoneAskingAndLoggedAsAutomatic() {
        givenLiveSubscription();
        AdCampaign campaign = givenActiveCampaignWithStaleCreatives(LocalDateTime.now().minusHours(2));

        assertTrue(activeCampaignService.autoNotifyIfCreativeChangesArePending(BUSINESS_ID));

        CampaignSwapEvent event = campaignSwapEventRepository.findAll().get(0);
        assertEquals(CampaignSwapEventType.AUTO_NOTIFY, event.getEventType());
        assertEquals(campaign.getCampaignId().getCampaignId(), event.getToCampaignId());
        assertEquals(event.getFromCampaignId(), event.getToCampaignId(), "a notify changes nothing");
        assertNull(event.getTriggeredByUserId(), "nobody clicked anything");
    }

    /**
     * A manual notify does not cancel the sweep explicitly — it just makes the pending condition
     * false. Worth its own test because that indirection is easy to break by "optimising" the
     * re-check away and trusting the sweep's query.
     */
    @Test
    void aManualNotifyInBetween_silencesTheSweepWithoutCancellingAnything() {
        givenLiveSubscription();
        givenActiveCampaignWithStaleCreatives(LocalDateTime.now().minusHours(2));
        givenNotificationEvent(CampaignSwapEventType.MANUAL_NOTIFY, LocalDateTime.now().minusMinutes(5));

        assertFalse(activeCampaignService.autoNotifyIfCreativeChangesArePending(BUSINESS_ID));
        assertTrue(campaignSwapEventRepository.findAll().stream()
                        .noneMatch(e -> e.getEventType() == CampaignSwapEventType.AUTO_NOTIFY),
                "the sweep must not send a second email for changes already announced");
    }

    /** Changes made after the last notification are pending again, however recent that was. */
    @Test
    void creativesChangedAfterTheLastNotification_arePendingAgain() {
        givenLiveSubscription();
        givenActiveCampaignWithStaleCreatives(LocalDateTime.now().minusHours(1));
        givenNotificationEvent(CampaignSwapEventType.SWAP, LocalDateTime.now().minusHours(3));

        assertTrue(activeCampaignService.autoNotifyIfCreativeChangesArePending(BUSINESS_ID));
    }

    /**
     * FR-8.5: no live subscription means no affected owners, so there must be no email and — the
     * part worth asserting — no event row either. The sweep must not manufacture one.
     */
    @Test
    void aBusinessWithNoLiveSubscription_isNotEvenACandidate() {
        givenActiveCampaignWithStaleCreatives(LocalDateTime.now().minusHours(2));

        assertTrue(candidates().isEmpty(), "no live subscription, so nothing to announce");
        assertTrue(campaignSwapEventRepository.findAll().isEmpty());
    }

    @Test
    void aCancelledSubscriptionDoesNotCountAsLive() {
        givenSubscription(BundleSubscriptionStatus.CANCELED);
        givenActiveCampaignWithStaleCreatives(LocalDateTime.now().minusHours(2));

        assertTrue(candidates().isEmpty());
    }

    /** A past-due subscriber's screens are still running, so their owners still need telling. */
    @Test
    void aPastDueSubscriptionStillCounts() {
        givenSubscription(BundleSubscriptionStatus.PAST_DUE);
        givenActiveCampaignWithStaleCreatives(LocalDateTime.now().minusHours(2));

        assertEquals(1, candidates().size());
    }

    /** The quiet period restarts on every edit, so an advertiser mid-upload is left alone. */
    @Test
    void changesInsideTheQuietPeriod_areNotYetSwept() {
        givenLiveSubscription();
        givenActiveCampaignWithStaleCreatives(LocalDateTime.now().minusMinutes(5));

        assertTrue(candidates().isEmpty(), "an advertiser still adding creatives must not be interrupted");
    }

    @Test
    void aCampaignThatWasNeverEdited_isNotACandidate() {
        givenLiveSubscription();
        AdCampaign campaign = givenCampaignWithAds("Summer Sale", 1);
        givenActiveCampaign(campaign); // creativesUpdatedAt left null

        assertTrue(candidates().isEmpty());
        assertFalse(activeCampaignService.autoNotifyIfCreativeChangesArePending(BUSINESS_ID));
    }

    @Test
    void aBusinessWithNothingOnScreen_isNotACandidate() {
        givenLiveSubscription();

        assertTrue(candidates().isEmpty());
        assertFalse(activeCampaignService.autoNotifyIfCreativeChangesArePending(BUSINESS_ID));
    }

    // ---------- helpers ----------

    private List<Business> candidates() {
        return businessRepository.findBusinessesWithCreativeChangesOlderThan(
                LocalDateTime.now().minusMinutes(60), BundleSubscriptionStatus.LIVE);
    }

    private AdCampaign givenCampaignWithAds(String name, int adCount) {
        AdCampaign campaign = new AdCampaign();
        campaign.setCampaignId(new AdCampaignIdentifier());
        campaign.setBusinessId(businessId);
        campaign.setName(name);
        for (int i = 0; i < adCount; i++) {
            Ad ad = new Ad();
            ad.setAdIdentifier(new AdIdentifier());
            ad.setName(name + " creative " + i);
            ad.setAdUrl("https://cdn.example.com/" + i + ".jpg");
            ad.setAdType(AdType.IMAGE);
            ad.setCampaign(campaign);
            campaign.getAds().add(ad);
        }
        return adCampaignRepository.save(campaign);
    }

    private void givenActiveCampaign(AdCampaign campaign) {
        Business business = businessRepository.findByBusinessId_BusinessId(BUSINESS_ID);
        business.setActiveCampaignId(campaign.getCampaignId().getCampaignId());
        businessRepository.save(business);
    }

    private AdCampaign givenActiveCampaignWithStaleCreatives(LocalDateTime creativesUpdatedAt) {
        AdCampaign campaign = givenCampaignWithAds("Summer Sale", 1);
        campaign.setCreativesUpdatedAt(creativesUpdatedAt);
        adCampaignRepository.save(campaign);
        givenActiveCampaign(campaign);
        return campaign;
    }

    /**
     * Forced in with SQL because {@code triggeredAt} is {@code @CreationTimestamp} and
     * {@code updatable = false}: Hibernate replaces whatever the entity carries with the insert
     * time and ignores a backdated value silently. Every assertion here is about an event being
     * older or newer than a creative change, so going through the repository would quietly
     * compare two "now"s and pass for the wrong reason.
     */
    private void givenNotificationEvent(CampaignSwapEventType type, LocalDateTime triggeredAt) {
        CampaignSwapEvent event = new CampaignSwapEvent();
        event.setBusinessId(BUSINESS_ID);
        event.setToCampaignId(businessRepository.findByBusinessId_BusinessId(BUSINESS_ID).getActiveCampaignId());
        event.setEventType(type);
        CampaignSwapEvent saved = campaignSwapEventRepository.save(event);
        jdbcTemplate.update("UPDATE campaign_swap_events SET triggered_at = ? WHERE id = ?",
                Timestamp.valueOf(triggeredAt), saved.getId());
    }

    private void givenLiveSubscription() {
        givenSubscription(BundleSubscriptionStatus.ACTIVE);
    }

    private void givenSubscription(BundleSubscriptionStatus status) {
        BundleSubscription subscription = new BundleSubscription();
        subscription.setBundleId("bundle-1");
        subscription.setAdvertiserBusinessId(BUSINESS_ID);
        subscription.setStripeCheckoutSessionId("cs_" + status + "_" + System.nanoTime());
        subscription.setStatus(status);
        subscription.setMonthlyAmount(new BigDecimal("10.00"));
        subscription.setScreenCount(1);
        bundleSubscriptionRepository.save(subscription);
    }
}
