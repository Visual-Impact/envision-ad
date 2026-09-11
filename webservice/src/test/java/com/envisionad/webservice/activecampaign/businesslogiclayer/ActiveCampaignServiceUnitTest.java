package com.envisionad.webservice.activecampaign.businesslogiclayer;

import com.envisionad.webservice.activecampaign.dataaccesslayer.CampaignSwapEvent;
import com.envisionad.webservice.activecampaign.dataaccesslayer.CampaignSwapEventRepository;
import com.envisionad.webservice.activecampaign.dataaccesslayer.CampaignSwapEventType;
import com.envisionad.webservice.activecampaign.exceptions.ActiveCampaignAlreadySetException;
import com.envisionad.webservice.activecampaign.exceptions.SwapDebounceException;
import com.envisionad.webservice.advertisement.dataaccesslayer.*;
import com.envisionad.webservice.advertisement.datamapperlayer.AdCampaignResponseMapper;
import com.envisionad.webservice.advertisement.datamapperlayer.AdResponseMapper;
import com.envisionad.webservice.advertisement.exceptions.AdCampaignNotFoundException;
import com.envisionad.webservice.advertisement.exceptions.CampaignHasNoAdsException;
import com.envisionad.webservice.business.dataaccesslayer.Business;
import com.envisionad.webservice.business.dataaccesslayer.BusinessIdentifier;
import com.envisionad.webservice.business.dataaccesslayer.BusinessRepository;
import com.envisionad.webservice.business.exceptions.BusinessNotFoundException;
import com.envisionad.webservice.media.DataAccessLayer.Media;
import com.envisionad.webservice.media.DataAccessLayer.MediaRepository;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionItemRepository;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionRepository;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionStatus;
import com.envisionad.webservice.utils.JwtUtils;
import com.envisionad.webservice.utils.MediaOwnerNotifier;
import com.envisionad.webservice.venue.dataaccesslayer.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * The rules that are easy to get subtly wrong: which events count against the cooldown, what a
 * no-op swap must not do, and the boundary between selecting for the first time and swapping.
 */
@ExtendWith(MockitoExtension.class)
class ActiveCampaignServiceUnitTest {

    private static final String BUSINESS_ID = "biz-1";
    private static final String CAMPAIGN_A = "camp-a";
    private static final String CAMPAIGN_B = "camp-b";
    private static final Duration DEBOUNCE = Duration.ofMinutes(10);

    @Mock private BusinessRepository businessRepository;
    @Mock private AdCampaignRepository adCampaignRepository;
    @Mock private CampaignSwapEventRepository campaignSwapEventRepository;
    @Mock private BundleSubscriptionRepository bundleSubscriptionRepository;
    @Mock private BundleSubscriptionItemRepository bundleSubscriptionItemRepository;
    @Mock private MediaRepository mediaRepository;
    @Mock private VenueRepository venueRepository;
    @Mock private AdCampaignResponseMapper adCampaignResponseMapper;
    @Mock private AdResponseMapper adResponseMapper;
    @Mock private MediaOwnerNotifier mediaOwnerNotifier;
    @Mock private JwtUtils jwtUtils;

    private ActiveCampaignServiceImpl service;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        // The distributor is real: its rules are covered by their own test, and stubbing it here
        // would hide whether the service actually feeds it the right screens.
        service = new ActiveCampaignServiceImpl(businessRepository, adCampaignRepository,
                campaignSwapEventRepository, bundleSubscriptionRepository, bundleSubscriptionItemRepository,
                mediaRepository, venueRepository, adCampaignResponseMapper, adResponseMapper,
                new CreativeDistributor(), mediaOwnerNotifier, jwtUtils, DEBOUNCE);
        jwt = Jwt.withTokenValue("token").header("alg", "none").claim("sub", "auth0|user").build();
    }

    // ---------- select ----------

    @Test
    void select_setsThePointerAndLogsAnInitialSelectionWithoutEmailingAnyone() {
        Business business = givenBusiness(null);
        givenCampaign(CAMPAIGN_A, 1);
        givenNoSubscriptions();

        service.selectInitialActiveCampaign(BUSINESS_ID, CAMPAIGN_A);

        assertEquals(CAMPAIGN_A, business.getActiveCampaignId());
        verify(businessRepository).save(business);
        assertEquals(CampaignSwapEventType.INITIAL_SELECTION, capturedEvent().getEventType());
        verifyNoInteractions(mediaOwnerNotifier);
    }

    /**
     * The first pick must not double as a way to change what is on screen — that path emails
     * owners and is rate-limited, and this one is neither.
     */
    @Test
    void select_whenAPointerIsAlreadySet_isRejected() {
        givenBusiness(CAMPAIGN_A);

        assertThrows(ActiveCampaignAlreadySetException.class,
                () -> service.selectInitialActiveCampaign(BUSINESS_ID, CAMPAIGN_B));
        verify(businessRepository, never()).save(any());
        verifyNoInteractions(campaignSwapEventRepository);
    }

    @Test
    void select_aCampaignWithNoCreatives_isRejected() {
        givenBusiness(null);
        givenCampaign(CAMPAIGN_A, 0);

        assertThrows(CampaignHasNoAdsException.class,
                () -> service.selectInitialActiveCampaign(BUSINESS_ID, CAMPAIGN_A));
        verify(businessRepository, never()).save(any());
    }

    /** A campaign belonging to someone else is indistinguishable from one that does not exist. */
    @Test
    void select_aCampaignOwnedByAnotherBusiness_isNotFound() {
        givenBusiness(null);
        AdCampaign foreign = campaign(CAMPAIGN_A, 1);
        foreign.setBusinessId(new BusinessIdentifier("someone-else"));
        when(adCampaignRepository.findByCampaignIdWithAds(CAMPAIGN_A)).thenReturn(foreign);

        assertThrows(AdCampaignNotFoundException.class,
                () -> service.selectInitialActiveCampaign(BUSINESS_ID, CAMPAIGN_A));
    }

    @Test
    void anUnknownBusiness_isNotFound() {
        when(businessRepository.findByBusinessId_BusinessId(BUSINESS_ID)).thenReturn(null);

        assertThrows(BusinessNotFoundException.class,
                () -> service.selectInitialActiveCampaign(BUSINESS_ID, CAMPAIGN_A));
    }

    // ---------- swap ----------

    @Test
    void swap_movesThePointerNotifiesOwnersAndRecordsTheRealCounts() {
        Business business = givenBusiness(CAMPAIGN_A);
        givenCampaign(CAMPAIGN_B, 1);
        givenNoRecentEvent();
        givenOneAffectedScreen();
        when(mediaOwnerNotifier.send(anyList())).thenReturn(new MediaOwnerNotifier.Outcome(3, 1));

        service.swapActiveCampaign(jwt, BUSINESS_ID, CAMPAIGN_B);

        assertEquals(CAMPAIGN_B, business.getActiveCampaignId());
        CampaignSwapEvent event = capturedEvent();
        assertEquals(CampaignSwapEventType.SWAP, event.getEventType());
        assertEquals(CAMPAIGN_A, event.getFromCampaignId());
        assertEquals(CAMPAIGN_B, event.getToCampaignId());
        assertEquals(3, event.getRecipientsNotified());
        assertEquals(1, event.getRecipientsFailed(), "an owner who should have heard and didn't is a failure");
    }

    /**
     * A double-submit must cost nothing. In particular it must not burn the cooldown — otherwise
     * an accidental second click locks the advertiser out of a correction for ten minutes.
     */
    @Test
    void swap_toTheCampaignAlreadyActive_isANoOpThatSpendsNothing() {
        givenBusiness(CAMPAIGN_A);
        givenCampaign(CAMPAIGN_A, 1);
        givenNoSubscriptions();

        service.swapActiveCampaign(jwt, BUSINESS_ID, CAMPAIGN_A);

        verify(businessRepository, never()).save(any());
        verify(campaignSwapEventRepository, never()).save(any());
        verifyNoInteractions(mediaOwnerNotifier);
    }

    @Test
    void swap_insideTheCooldown_isRefusedWithTheRemainingSeconds() {
        givenBusiness(CAMPAIGN_A);
        givenCampaign(CAMPAIGN_B, 1);
        givenRecentEvent(CampaignSwapEventType.SWAP, LocalDateTime.now().minusMinutes(4));

        SwapDebounceException thrown = assertThrows(SwapDebounceException.class,
                () -> service.swapActiveCampaign(jwt, BUSINESS_ID, CAMPAIGN_B));

        assertTrue(thrown.getRetryAfterSeconds() > 5 * 60 - 30
                        && thrown.getRetryAfterSeconds() <= 6 * 60,
                "about six minutes should remain, was " + thrown.getRetryAfterSeconds());
        verify(businessRepository, never()).save(any());
        verifyNoInteractions(mediaOwnerNotifier);
    }

    @Test
    void swap_justOutsideTheCooldown_isAllowed() {
        givenBusiness(CAMPAIGN_A);
        givenCampaign(CAMPAIGN_B, 1);
        givenRecentEvent(CampaignSwapEventType.SWAP, LocalDateTime.now().minusMinutes(10).minusSeconds(1));
        givenOneAffectedScreen();
        when(mediaOwnerNotifier.send(anyList())).thenReturn(new MediaOwnerNotifier.Outcome(1, 0));

        assertDoesNotThrow(() -> service.swapActiveCampaign(jwt, BUSINESS_ID, CAMPAIGN_B));
    }

    /**
     * The automatic sweep must never suppress a person's action. It is rate-limited far more
     * strictly by its own quiet period, and counting it here would let the backstop lock the
     * advertiser out of the dashboard button.
     */
    @Test
    void swap_isNotBlockedByARecentAutomaticNotification() {
        givenBusiness(CAMPAIGN_A);
        givenCampaign(CAMPAIGN_B, 1);
        givenOneAffectedScreen();
        when(mediaOwnerNotifier.send(anyList())).thenReturn(new MediaOwnerNotifier.Outcome(1, 0));
        // The debounce query asks only for human events, so an AUTO_NOTIFY row is simply not
        // returned. Asserting on the query's arguments is what proves the exclusion.
        when(campaignSwapEventRepository.findTopByBusinessIdAndEventTypeInOrderByTriggeredAtDesc(
                eq(BUSINESS_ID), anyList())).thenReturn(Optional.empty());

        service.swapActiveCampaign(jwt, BUSINESS_ID, CAMPAIGN_B);

        ArgumentCaptor<List<CampaignSwapEventType>> types = ArgumentCaptor.forClass(List.class);
        verify(campaignSwapEventRepository, atLeastOnce())
                .findTopByBusinessIdAndEventTypeInOrderByTriggeredAtDesc(eq(BUSINESS_ID), types.capture());
        assertFalse(types.getAllValues().stream().anyMatch(t -> t.contains(CampaignSwapEventType.AUTO_NOTIFY)),
                "AUTO_NOTIFY must never count toward the human cooldown");
    }

    @Test
    void swap_isBlockedByARecentManualNotify_becauseBothSendTheSameEmail() {
        givenBusiness(CAMPAIGN_A);
        givenCampaign(CAMPAIGN_B, 1);
        givenRecentEvent(CampaignSwapEventType.MANUAL_NOTIFY, LocalDateTime.now().minusMinutes(1));

        assertThrows(SwapDebounceException.class,
                () -> service.swapActiveCampaign(jwt, BUSINESS_ID, CAMPAIGN_B));
    }

    @Test
    void swap_requiresTheCallerToBelongToTheBusiness() {
        doThrow(new org.springframework.security.access.AccessDeniedException("nope"))
                .when(jwtUtils).validateUserIsEmployeeOfBusiness(jwt, BUSINESS_ID);

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.swapActiveCampaign(jwt, BUSINESS_ID, CAMPAIGN_B));
        verifyNoInteractions(businessRepository, mediaOwnerNotifier);
    }

    // ---------- notify ----------

    @Test
    void notify_withNoActiveCampaign_isNotFound() {
        givenBusiness(null);

        assertThrows(AdCampaignNotFoundException.class, () -> service.notifyMediaOwners(jwt, BUSINESS_ID));
        verifyNoInteractions(mediaOwnerNotifier);
    }

    @Test
    void notify_recordsAManualNotifyAndReturnsTheCounts() {
        givenBusiness(CAMPAIGN_A);
        givenCampaign(CAMPAIGN_A, 1);
        givenNoRecentEvent();
        givenOneAffectedScreen();
        when(mediaOwnerNotifier.send(anyList())).thenReturn(new MediaOwnerNotifier.Outcome(2, 0));

        var result = service.notifyMediaOwners(jwt, BUSINESS_ID);

        assertEquals(2, result.getNotifiedCount());
        CampaignSwapEvent event = capturedEvent();
        assertEquals(CampaignSwapEventType.MANUAL_NOTIFY, event.getEventType());
        assertEquals(event.getFromCampaignId(), event.getToCampaignId(), "a notify changes nothing");
    }

    // ---------- affected media ----------

    /** FR-4.1: an advertiser's creatives never go up in a venue of their own business type. */
    @Test
    void resolveAffectedMedia_excludesScreensInTheAdvertisersOwnBusinessTypeVenue() {
        Business business = business(CAMPAIGN_A);
        business.setBusinessTypeVenueId("venue-gym");
        Media rivalGym = screen("venue-gym");
        Media cafe = screen("venue-cafe");
        when(bundleSubscriptionItemRepository.findDistinctMediaIdsByAdvertiserBusinessId(
                BUSINESS_ID, BundleSubscriptionStatus.LIVE)).thenReturn(List.of(rivalGym.getId(), cafe.getId()));
        when(mediaRepository.findAllByIdWithLocation(anyList())).thenReturn(List.of(rivalGym, cafe));

        List<Media> affected = service.resolveAffectedMediaForBusiness(business);

        assertEquals(List.of(cafe), affected, "a gym must not advertise on a rival gym's screen");
    }

    @Test
    void resolveAffectedMedia_withNoBusinessTypeSet_excludesNothing() {
        Business business = business(CAMPAIGN_A);
        Media gym = screen("venue-gym");
        when(bundleSubscriptionItemRepository.findDistinctMediaIdsByAdvertiserBusinessId(
                BUSINESS_ID, BundleSubscriptionStatus.LIVE)).thenReturn(List.of(gym.getId()));
        when(mediaRepository.findAllByIdWithLocation(anyList())).thenReturn(List.of(gym));

        assertEquals(List.of(gym), service.resolveAffectedMediaForBusiness(business));
    }

    // ---------- the rendered email (FR-4.6 / FR-8.6) ----------
    // Nothing else asserts what media owners actually read. The live sweep test only ever
    // exercised the flat format, because every local owner has an unclassified screen.

    /**
     * The per-venue layout, end to end through the swap path: the universal creative stated
     * once and unheaded, then one header per venue with only that venue's creatives, then the
     * screens. Also pins the swap framing — "updated the campaign displayed" — which is what
     * tells an owner this is a different campaign, not a correction to the one they have.
     */
    @Test
    void swapEmail_forAMultiVenueOwner_rendersPerVenueSectionsWithTheSwapFraming() {
        givenBusiness(CAMPAIGN_A);
        givenNoRecentEvent();

        Ad universal = taggedAd("Brand Spot");
        Ad gymOnly = taggedAd("Protein Shake", "venue-gym");
        Ad barberOnly = taggedAd("Beard Oil", "venue-barber");
        AdCampaign target = campaign(CAMPAIGN_B, 0);
        target.setName("Autumn Launch");
        target.setAds(new java.util.ArrayList<>(List.of(universal, gymOnly, barberOnly)));
        when(adCampaignRepository.findByCampaignIdWithAds(CAMPAIGN_B)).thenReturn(target);

        UUID owner = UUID.randomUUID();
        Media gymScreen = screen("venue-gym");
        gymScreen.setBusinessId(owner);
        gymScreen.setTitle("Gym Lobby");
        Media barberScreen = screen("venue-barber");
        barberScreen.setBusinessId(owner);
        barberScreen.setTitle("Barber Waiting Room");
        when(bundleSubscriptionItemRepository.findDistinctMediaIdsByAdvertiserBusinessId(
                BUSINESS_ID, BundleSubscriptionStatus.LIVE))
                .thenReturn(List.of(gymScreen.getId(), barberScreen.getId()));
        when(mediaRepository.findAllByIdWithLocation(anyList())).thenReturn(List.of(gymScreen, barberScreen));
        when(venueRepository.findAll()).thenReturn(List.of(venue("venue-gym", "Gym"), venue("venue-barber", "Barbershop")));
        when(mediaOwnerNotifier.send(anyList())).thenReturn(new MediaOwnerNotifier.Outcome(1, 0));

        service.swapActiveCampaign(jwt, BUSINESS_ID, CAMPAIGN_B);

        MediaOwnerNotifier.OwnerMessage message = singleSentMessage();
        assertEquals(owner.toString(), message.ownerBusinessId());
        assertEquals("New creatives for your Envision Ad screens — Acme Co", message.subject());
        String body = message.body();
        assertTrue(body.contains("Acme Co has updated the campaign displayed on your screens to \"Autumn Launch\"."),
                "swap framing: " + body);
        assertFalse(body.contains("already running"), "a swap must not read as an update to the current campaign");

        int universalAt = body.indexOf("- Brand Spot (IMAGE): ");
        int gymHeaderAt = body.indexOf("For your Gym screens:");
        int gymAdAt = body.indexOf("- Protein Shake (IMAGE): ");
        int barberHeaderAt = body.indexOf("For your Barbershop screens:");
        int barberAdAt = body.indexOf("- Beard Oil (IMAGE): ");
        assertTrue(universalAt >= 0 && universalAt < gymHeaderAt, "universal creative first, unheaded: " + body);
        assertTrue(gymHeaderAt < gymAdAt && gymAdAt < barberHeaderAt, "gym section holds the gym creative: " + body);
        assertTrue(barberHeaderAt < barberAdAt, "barbershop section holds the barber creative: " + body);
        assertEquals(1, body.split("Brand Spot", -1).length - 1, "the universal creative is listed exactly once");
        assertTrue(body.contains("Screens affected: Gym Lobby, Barber Waiting Room"), body);
    }

    /**
     * FR-8.6: a notify about the campaign already on screen must say so. An owner who gets a
     * swap-shaped email for a campaign they already have loaded would reasonably take it for a
     * duplicate and ignore it — leaving the retired creative up, which defeats FR-8 entirely.
     */
    @Test
    void notifyEmail_usesTheUpdateFramingNotTheSwapFraming() {
        givenBusiness(CAMPAIGN_A);
        givenCampaign(CAMPAIGN_A, 1);
        givenNoRecentEvent();
        givenOneAffectedScreen();
        when(mediaOwnerNotifier.send(anyList())).thenReturn(new MediaOwnerNotifier.Outcome(1, 0));

        service.notifyMediaOwners(jwt, BUSINESS_ID);

        String body = singleSentMessage().body();
        assertTrue(body.contains("Acme Co has changed the creatives for \"Campaign camp-a\", "
                + "the campaign already running on your screens."), body);
        assertTrue(body.contains("Please replace the previous set with the following:"), body);
        assertFalse(body.contains("has updated the campaign displayed"), body);
        assertFalse(body.contains("For your "), "a single-venue owner gets a flat list, no headers: " + body);
    }

    private MediaOwnerNotifier.OwnerMessage singleSentMessage() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MediaOwnerNotifier.OwnerMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(mediaOwnerNotifier).send(captor.capture());
        assertEquals(1, captor.getValue().size(), "expected exactly one owner message");
        return captor.getValue().get(0);
    }

    private Ad taggedAd(String name, String... venueIds) {
        Ad ad = new Ad();
        ad.setAdIdentifier(new AdIdentifier());
        ad.setName(name);
        ad.setAdUrl("https://cdn.example.com/" + name.replace(' ', '-') + ".jpg");
        ad.setAdType(AdType.IMAGE);
        ad.setVenues(java.util.Arrays.stream(venueIds).map(id -> venue(id, id)).toList());
        return ad;
    }

    private com.envisionad.webservice.venue.dataaccesslayer.Venue venue(String venueId, String nameEn) {
        var venue = new com.envisionad.webservice.venue.dataaccesslayer.Venue();
        venue.setVenueId(venueId);
        venue.setNameEn(nameEn);
        return venue;
    }

    // ---------- helpers ----------

    private Business business(String activeCampaignId) {
        Business business = new Business();
        business.setBusinessId(new BusinessIdentifier(BUSINESS_ID));
        business.setName("Acme Co");
        business.setActiveCampaignId(activeCampaignId);
        return business;
    }

    /** For the tests that go in through a public entry point and so need the lookup stubbed. */
    private Business givenBusiness(String activeCampaignId) {
        Business business = business(activeCampaignId);
        when(businessRepository.findByBusinessId_BusinessId(BUSINESS_ID)).thenReturn(business);
        return business;
    }

    private AdCampaign campaign(String campaignId, int adCount) {
        AdCampaign campaign = new AdCampaign();
        campaign.setCampaignId(new AdCampaignIdentifier(campaignId));
        campaign.setBusinessId(new BusinessIdentifier(BUSINESS_ID));
        campaign.setName("Campaign " + campaignId);
        List<Ad> ads = new java.util.ArrayList<>();
        for (int i = 0; i < adCount; i++) {
            Ad ad = new Ad();
            ad.setAdIdentifier(new AdIdentifier());
            ad.setName("Creative " + i);
            ad.setAdUrl("https://cdn.example.com/" + i + ".jpg");
            ad.setAdType(AdType.IMAGE);
            ad.setVenues(List.of());
            ads.add(ad);
        }
        campaign.setAds(ads);
        return campaign;
    }

    private void givenCampaign(String campaignId, int adCount) {
        when(adCampaignRepository.findByCampaignIdWithAds(campaignId)).thenReturn(campaign(campaignId, adCount));
    }

    private Media screen(String venueId) {
        Media media = new Media();
        media.setId(UUID.randomUUID());
        media.setTitle("Screen " + venueId);
        media.setVenueId(venueId);
        media.setBusinessId(UUID.randomUUID());
        return media;
    }

    private void givenOneAffectedScreen() {
        Media media = screen("venue-gym");
        when(bundleSubscriptionItemRepository.findDistinctMediaIdsByAdvertiserBusinessId(
                BUSINESS_ID, BundleSubscriptionStatus.LIVE)).thenReturn(List.of(media.getId()));
        when(mediaRepository.findAllByIdWithLocation(anyList())).thenReturn(List.of(media));
        when(venueRepository.findAll()).thenReturn(List.of());
    }

    private void givenNoSubscriptions() {
        lenient().when(bundleSubscriptionItemRepository.findDistinctMediaIdsByAdvertiserBusinessId(
                BUSINESS_ID, BundleSubscriptionStatus.LIVE)).thenReturn(List.of());
        lenient().when(bundleSubscriptionRepository.findAllByAdvertiserBusinessIdAndStatusIn(
                BUSINESS_ID, BundleSubscriptionStatus.LIVE)).thenReturn(List.of());
    }

    private void givenNoRecentEvent() {
        when(campaignSwapEventRepository.findTopByBusinessIdAndEventTypeInOrderByTriggeredAtDesc(
                eq(BUSINESS_ID), anyList())).thenReturn(Optional.empty());
    }

    private void givenRecentEvent(CampaignSwapEventType type, LocalDateTime triggeredAt) {
        CampaignSwapEvent event = new CampaignSwapEvent();
        event.setBusinessId(BUSINESS_ID);
        event.setEventType(type);
        event.setTriggeredAt(triggeredAt);
        when(campaignSwapEventRepository.findTopByBusinessIdAndEventTypeInOrderByTriggeredAtDesc(
                eq(BUSINESS_ID), anyList())).thenReturn(Optional.of(event));
    }

    private CampaignSwapEvent capturedEvent() {
        ArgumentCaptor<CampaignSwapEvent> captor = ArgumentCaptor.forClass(CampaignSwapEvent.class);
        verify(campaignSwapEventRepository).save(captor.capture());
        return captor.getValue();
    }
}
