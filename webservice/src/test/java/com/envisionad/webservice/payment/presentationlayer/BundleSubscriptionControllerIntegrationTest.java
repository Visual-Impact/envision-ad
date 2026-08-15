package com.envisionad.webservice.payment.presentationlayer;

import com.envisionad.webservice.advertisement.dataaccesslayer.*;
import com.envisionad.webservice.bundle.dataaccesslayer.Bundle;
import com.envisionad.webservice.bundle.dataaccesslayer.BundleRepository;
import com.envisionad.webservice.bundle.dataaccesslayer.BundleRuleType;
import com.envisionad.webservice.business.dataaccesslayer.Business;
import com.envisionad.webservice.business.dataaccesslayer.BusinessIdentifier;
import com.envisionad.webservice.business.dataaccesslayer.BusinessRepository;
import com.envisionad.webservice.business.dataaccesslayer.Employee;
import com.envisionad.webservice.business.dataaccesslayer.EmployeeIdentifier;
import com.envisionad.webservice.business.dataaccesslayer.EmployeeRepository;
import com.envisionad.webservice.config.BaseIntegrationTest;
import com.envisionad.webservice.media.DataAccessLayer.*;
import com.envisionad.webservice.payment.dataaccesslayer.*;
import com.envisionad.webservice.payment.presentationlayer.models.BundleSubscriptionRequestModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Covers every subscribe path that short-circuits <strong>before</strong> the first
 * Stripe call. That split is deliberate: {@code BaseIntegrationTest} forbids extra
 * {@code @MockitoBean}s, so Stripe cannot be stubbed here — the happy path and the
 * Stripe request shape live in {@link
 * com.envisionad.webservice.payment.businesslogiclayer.BundleSubscriptionServiceUnitTest}
 * instead, and the service orders its guards so this file can reach all of them.
 */
class BundleSubscriptionControllerIntegrationTest extends BaseIntegrationTest {

    private static final String BASE_URI = "/api/v1/bundle-subscriptions";
    private static final String TOKEN = "advertiser-token";
    private static final String USER_ID = "auth0|advertiser123";

    @Autowired
    private BundleRepository bundleRepository;

    @Autowired
    private BundleSubscriptionRepository subscriptionRepository;

    @Autowired
    private BundleSubscriptionItemRepository subscriptionItemRepository;

    @Autowired
    private AdCampaignRepository adCampaignRepository;

    @Autowired
    private MediaRepository mediaRepository;

    @Autowired
    private MediaLocationRepository mediaLocationRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private BusinessRepository businessRepository;

    private String businessId;
    private Bundle bundle;
    private AdCampaign campaign;

    @BeforeEach
    void setUp() {
        subscriptionItemRepository.deleteAll();
        subscriptionRepository.deleteAll();
        bundleRepository.deleteAll();
        adCampaignRepository.deleteAll();
        mediaRepository.deleteAll();
        mediaLocationRepository.deleteAll();
        employeeRepository.deleteAll();
        businessRepository.deleteAll();

        Jwt jwt = Jwt.withTokenValue(TOKEN)
                .header("alg", "none")
                .claim("sub", USER_ID)
                .build();
        when(jwtDecoder.decode(anyString())).thenReturn(jwt);

        businessId = UUID.randomUUID().toString();
        Employee employee = new Employee();
        employee.setEmployeeId(new EmployeeIdentifier());
        employee.setBusinessId(new BusinessIdentifier(businessId));
        employee.setUserId(USER_ID);
        employeeRepository.save(employee);
        givenBusiness(businessId, true);

        givenMedia(Status.ACTIVE, "4.00");
        bundle = givenBundle(true);
        campaign = givenCampaign(businessId, true);
    }

    // ---------- guards ----------

    @Test
    void subscribe_withUnknownBundle_returnsNotFound() {
        postSubscribe(request("no-such-bundle", campaign.getCampaignId().getCampaignId(), businessId))
                .expectStatus().isNotFound();

        assertTrue(subscriptionRepository.findAll().isEmpty());
    }

    @Test
    void subscribe_withAnUnknownCouponCode_returnsBadRequestWithoutReachingStripe() {
        BundleSubscriptionRequestModel body = request(
                bundle.getBundleId(), campaign.getCampaignId().getCampaignId(), businessId);
        body.setCouponCode("NO-SUCH-CODE");

        postSubscribe(body).expectStatus().isBadRequest();

        assertTrue(subscriptionRepository.findAll().isEmpty());
    }

    @Test
    void subscribe_withInactiveBundle_returnsConflict() {
        Bundle inactive = givenBundle(false);

        postSubscribe(request(inactive.getBundleId(), campaign.getCampaignId().getCampaignId(), businessId))
                .expectStatus().isEqualTo(409);
    }

    /** An advertiser whose organization has not cleared admin verification cannot subscribe. */
    @Test
    void subscribe_forAnUnverifiedBusiness_returnsForbidden() {
        givenBusiness(businessId, false);

        postSubscribe(request(bundle.getBundleId(), campaign.getCampaignId().getCampaignId(), businessId))
                .expectStatus().isForbidden();

        assertTrue(subscriptionRepository.findAll().isEmpty());
    }

    @Test
    void subscribe_forABusinessTheUserDoesNotBelongTo_returnsForbidden() {
        postSubscribe(request(bundle.getBundleId(), campaign.getCampaignId().getCampaignId(),
                UUID.randomUUID().toString()))
                .expectStatus().isForbidden();
    }

    @Test
    void subscribe_withoutAToken_isRejected() {
        webTestClient.post().uri(BASE_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request(bundle.getBundleId(), campaign.getCampaignId().getCampaignId(), businessId))
                .exchange()
                .expectStatus().value(status -> assertTrue(status == 401 || status == 403,
                        "anonymous callers must be rejected, got " + status));
    }

    @Test
    void subscribe_withUnknownCampaign_returnsNotFound() {
        postSubscribe(request(bundle.getBundleId(), "no-such-campaign", businessId))
                .expectStatus().isNotFound();
    }

    @Test
    void subscribe_withACampaignOwnedByAnotherBusiness_returnsForbidden() {
        AdCampaign foreign = givenCampaign(UUID.randomUUID().toString(), true);

        postSubscribe(request(bundle.getBundleId(), foreign.getCampaignId().getCampaignId(), businessId))
                .expectStatus().isForbidden();
    }

    @Test
    void subscribe_withACampaignThatHasNoAds_returnsConflict() {
        AdCampaign empty = givenCampaign(businessId, false);

        postSubscribe(request(bundle.getBundleId(), empty.getCampaignId().getCampaignId(), businessId))
                .expectStatus().isEqualTo(409);

        assertTrue(subscriptionRepository.findAll().isEmpty());
    }

    @Test
    void subscribe_toABundleWithNoEligibleScreens_returnsConflict() {
        mediaRepository.deleteAll();

        postSubscribe(request(bundle.getBundleId(), campaign.getCampaignId().getCampaignId(), businessId))
                .expectStatus().isEqualTo(409);

        assertTrue(subscriptionRepository.findAll().isEmpty());
    }

    @Test
    void subscribe_whenOnlyInactiveScreensMatch_returnsConflict() {
        mediaRepository.deleteAll();
        givenMedia(Status.PENDING, "9.99");

        postSubscribe(request(bundle.getBundleId(), campaign.getCampaignId().getCampaignId(), businessId))
                .expectStatus().isEqualTo(409);
    }

    /**
     * The rule the partial unique index encodes, enforced at the service layer — which is
     * where it has to live, since that index is Flyway-only and absent from this
     * entity-generated test schema.
     */
    @Test
    void subscribe_whenTheBusinessAlreadyHasALiveSubscription_returnsConflict() {
        givenSubscription(bundle.getBundleId(), businessId, BundleSubscriptionStatus.ACTIVE);

        postSubscribe(request(bundle.getBundleId(), campaign.getCampaignId().getCampaignId(), businessId))
                .expectStatus().isEqualTo(409);

        assertEquals(1, subscriptionRepository.findAll().size());
    }

    @Test
    void subscribe_whenAPastDueSubscriptionExists_returnsConflict() {
        givenSubscription(bundle.getBundleId(), businessId, BundleSubscriptionStatus.PAST_DUE);

        postSubscribe(request(bundle.getBundleId(), campaign.getCampaignId().getCampaignId(), businessId))
                .expectStatus().isEqualTo(409);
    }

    /**
     * CANCELED is history and INCOMPLETE is an abandoned checkout — neither may block a
     * fresh attempt. Asserted against the repository query the guard uses rather than
     * over HTTP, because getting past the guard means reaching {@code Session.create},
     * and the test profile's dummy API key would turn that into a real network call.
     * The reuse behaviour itself is covered in the service unit test.
     */
    @Test
    void theDuplicateGuardIgnoresCanceledAndIncompleteSubscriptions() {
        givenSubscription(bundle.getBundleId(), businessId, BundleSubscriptionStatus.CANCELED);
        assertTrue(subscriptionRepository.findByBundleIdAndAdvertiserBusinessIdAndStatusIn(
                        bundle.getBundleId(), businessId,
                        List.of(BundleSubscriptionStatus.ACTIVE, BundleSubscriptionStatus.PAST_DUE))
                .isEmpty());

        subscriptionRepository.deleteAll();
        givenSubscription(bundle.getBundleId(), businessId, BundleSubscriptionStatus.INCOMPLETE);
        assertTrue(subscriptionRepository.findByBundleIdAndAdvertiserBusinessIdAndStatusIn(
                        bundle.getBundleId(), businessId,
                        List.of(BundleSubscriptionStatus.ACTIVE, BundleSubscriptionStatus.PAST_DUE))
                .isEmpty());
        assertTrue(subscriptionRepository.findByBundleIdAndAdvertiserBusinessIdAndStatusIn(
                        bundle.getBundleId(), businessId, List.of(BundleSubscriptionStatus.INCOMPLETE))
                .isPresent(), "the INCOMPLETE row must be findable for reuse");
    }

    // ---------- cancel ----------
    // Same split as subscribe: everything here short-circuits before the Stripe call.
    // The one path that does reach Stripe — an ACTIVE subscription with a linked
    // stripe_subscription_id — is covered in BundleSubscriptionServiceUnitTest.

    @Test
    void cancel_withUnknownSubscription_returnsNotFound() {
        postCancel("no-such-subscription").expectStatus().isNotFound();
    }

    @Test
    void cancel_aSubscriptionBelongingToAnotherBusiness_returnsForbidden() {
        BundleSubscription foreign = givenSubscription(
                bundle.getBundleId(), UUID.randomUUID().toString(), BundleSubscriptionStatus.ACTIVE);

        postCancel(foreign.getSubscriptionId()).expectStatus().isForbidden();

        assertEquals(BundleSubscriptionStatus.ACTIVE,
                subscriptionRepository.findBySubscriptionId(foreign.getSubscriptionId())
                        .orElseThrow().getStatus());
    }

    @Test
    void cancel_withoutAToken_isRejected() {
        BundleSubscription subscription = givenSubscription(
                bundle.getBundleId(), businessId, BundleSubscriptionStatus.ACTIVE);

        webTestClient.post().uri(BASE_URI + "/" + subscription.getSubscriptionId() + "/cancel")
                .exchange()
                .expectStatus().value(status -> assertTrue(status == 401 || status == 403,
                        "anonymous callers must be rejected, got " + status));
    }

    /** Double-clicking cancel should not produce an error. */
    @Test
    void cancel_anAlreadyCanceledSubscription_succeedsQuietly() {
        BundleSubscription subscription = givenSubscription(
                bundle.getBundleId(), businessId, BundleSubscriptionStatus.CANCELED);

        postCancel(subscription.getSubscriptionId()).expectStatus().isNoContent();

        assertEquals(BundleSubscriptionStatus.CANCELED,
                subscriptionRepository.findBySubscriptionId(subscription.getSubscriptionId())
                        .orElseThrow().getStatus());
    }

    /**
     * An abandoned checkout has no Stripe subscription to cancel. Closing it out
     * locally frees the buyer's one-live-subscription-per-bundle slot instead of
     * leaving it stuck INCOMPLETE forever.
     */
    @Test
    void cancel_anIncompleteSubscription_closesItLocallyWithoutCallingStripe() {
        BundleSubscription subscription = givenSubscription(
                bundle.getBundleId(), businessId, BundleSubscriptionStatus.INCOMPLETE);

        postCancel(subscription.getSubscriptionId()).expectStatus().isNoContent();

        BundleSubscription reloaded = subscriptionRepository
                .findBySubscriptionId(subscription.getSubscriptionId()).orElseThrow();
        assertEquals(BundleSubscriptionStatus.CANCELED, reloaded.getStatus());
        assertNotNull(reloaded.getCanceledAt());
    }

    // ---------- list (M6) ----------

    /**
     * The list deliberately returns every status, INCOMPLETE included: an abandoned checkout
     * occupies the buyer's one-live-subscription slot for that bundle (D22), and the advertiser
     * cannot clear what they cannot see.
     */
    @Test
    void listSubscriptions_returnsEveryStatusForTheBusiness() {
        givenSubscription(bundle.getBundleId(), businessId, BundleSubscriptionStatus.ACTIVE);
        Bundle second = givenBundle(true);
        givenSubscription(second.getBundleId(), businessId, BundleSubscriptionStatus.INCOMPLETE);
        Bundle third = givenBundle(true);
        givenSubscription(third.getBundleId(), businessId, BundleSubscriptionStatus.CANCELED);

        getSubscriptions(businessId)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(3);
    }

    @Test
    void listSubscriptions_carriesTheBundleNameAndLockedFigures() {
        givenSubscription(bundle.getBundleId(), businessId, BundleSubscriptionStatus.ACTIVE);

        getSubscriptions(businessId)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].bundleNameEn").isEqualTo("Full Network")
                .jsonPath("$[0].bundleNameFr").isEqualTo("Réseau complet")
                .jsonPath("$[0].status").isEqualTo("ACTIVE")
                .jsonPath("$[0].monthlyAmount").isEqualTo(4.00)
                .jsonPath("$[0].screenCount").isEqualTo(1)
                .jsonPath("$[0].campaignId").isEqualTo(campaign.getCampaignId().getCampaignId());
    }

    /**
     * A NULL renewal date is reachable state, not a fault: checkout.session.completed activates a
     * row without one and only invoice.paid sets it. The endpoint must serialise the absence
     * rather than substituting a value.
     */
    @Test
    void listSubscriptions_serialisesANullRenewalDate() {
        givenSubscription(bundle.getBundleId(), businessId, BundleSubscriptionStatus.ACTIVE);

        getSubscriptions(businessId)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].currentPeriodEnd").doesNotExist()
                .jsonPath("$[0].cancelAtPeriodEnd").isEqualTo(false);
    }

    /** A cancelled-but-not-yet-ended subscription is ACTIVE with the flag set — the UI needs both. */
    @Test
    void listSubscriptions_distinguishesCancelAtPeriodEndFromPlainActive() {
        BundleSubscription subscription =
                givenSubscription(bundle.getBundleId(), businessId, BundleSubscriptionStatus.ACTIVE);
        subscription.setCancelAtPeriodEnd(true);
        subscriptionRepository.save(subscription);

        getSubscriptions(businessId)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].status").isEqualTo("ACTIVE")
                .jsonPath("$[0].cancelAtPeriodEnd").isEqualTo(true);
    }

    @Test
    void listSubscriptions_returnsEmptyWhenTheBusinessHasNone() {
        getSubscriptions(businessId)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(0);
    }

    @Test
    void listSubscriptions_forAnotherBusiness_returnsForbidden() {
        getSubscriptions(UUID.randomUUID().toString())
                .expectStatus().isForbidden();
    }

    @Test
    void listSubscriptions_withoutAToken_isRejected() {
        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path(BASE_URI).queryParam("businessId", businessId).build())
                .exchange()
                .expectStatus().value(status -> assertTrue(status == 401 || status == 403,
                        "anonymous callers must be rejected, got " + status));
    }

    // ---------- live campaigns for proof of display (D40) ----------

    @Test
    void liveCampaigns_returnsCampaignsRunningOnTheOwnersScreen() {
        UUID mediaId = givenMediaOwnedBy(businessId);
        BundleSubscription subscription =
                givenSubscription(bundle.getBundleId(), businessId, BundleSubscriptionStatus.ACTIVE);
        givenSubscriptionItem(subscription.getSubscriptionId(), mediaId);

        getLiveCampaigns(mediaId.toString())
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].campaignId").isEqualTo(campaign.getCampaignId().getCampaignId());
    }

    /** PAST_DUE still counts as running — the screens keep playing until cancellation (D40). */
    @Test
    void liveCampaigns_includesPastDueSubscriptions() {
        UUID mediaId = givenMediaOwnedBy(businessId);
        BundleSubscription subscription =
                givenSubscription(bundle.getBundleId(), businessId, BundleSubscriptionStatus.PAST_DUE);
        givenSubscriptionItem(subscription.getSubscriptionId(), mediaId);

        getLiveCampaigns(mediaId.toString())
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1);
    }

    @Test
    void liveCampaigns_excludesCanceledAndIncompleteSubscriptions() {
        UUID mediaId = givenMediaOwnedBy(businessId);
        BundleSubscription canceled =
                givenSubscription(bundle.getBundleId(), businessId, BundleSubscriptionStatus.CANCELED);
        givenSubscriptionItem(canceled.getSubscriptionId(), mediaId);
        Bundle other = givenBundle(true);
        BundleSubscription incomplete =
                givenSubscription(other.getBundleId(), businessId, BundleSubscriptionStatus.INCOMPLETE);
        givenSubscriptionItem(incomplete.getSubscriptionId(), mediaId);

        getLiveCampaigns(mediaId.toString())
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(0);
    }

    @Test
    void liveCampaigns_forAScreenOwnedByAnotherBusiness_returnsForbidden() {
        UUID mediaId = givenMediaOwnedBy(UUID.randomUUID().toString());

        getLiveCampaigns(mediaId.toString())
                .expectStatus().isForbidden();
    }

    @Test
    void liveCampaigns_forAnUnknownScreen_returnsNotFound() {
        getLiveCampaigns(UUID.randomUUID().toString())
                .expectStatus().isNotFound();
    }

    // ---------- fixtures ----------

    private org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec postCancel(
            String subscriptionId) {
        return webTestClient.post().uri(BASE_URI + "/" + subscriptionId + "/cancel")
                .header("Authorization", "Bearer " + TOKEN)
                .exchange();
    }

    private org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec postSubscribe(
            BundleSubscriptionRequestModel body) {
        return webTestClient.post().uri(BASE_URI)
                .header("Authorization", "Bearer " + TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange();
    }

    private BundleSubscriptionRequestModel request(String bundleId, String campaignId, String businessId) {
        return new BundleSubscriptionRequestModel(bundleId, campaignId, businessId, null);
    }

    private Business givenBusiness(String businessId, boolean verified) {
        Business business = businessRepository.findByBusinessId_BusinessId(businessId);
        if (business == null) {
            business = new Business();
            business.setBusinessId(new BusinessIdentifier(businessId));
            business.setName("Test Business");
        }
        business.setVerified(verified);
        return businessRepository.save(business);
    }

    private Bundle givenBundle(boolean active) {
        Bundle b = new Bundle();
        b.setNameEn("Full Network");
        b.setNameFr("Réseau complet");
        b.setBadgeColor("#3366FF");
        b.setRuleType(BundleRuleType.FULL_NETWORK);
        b.setRuleValue(null);
        b.setActive(active);
        return bundleRepository.save(b);
    }

    private AdCampaign givenCampaign(String ownerBusinessId, boolean withAd) {
        AdCampaign c = new AdCampaign();
        c.setCampaignId(new AdCampaignIdentifier());
        c.setBusinessId(new BusinessIdentifier(ownerBusinessId));
        c.setName("Spring campaign");
        if (withAd) {
            Ad ad = new Ad();
            ad.setAdIdentifier(new AdIdentifier());
            ad.setName("Banner");
            ad.setAdUrl("https://example.com/banner.png");
            ad.setAdType(AdType.IMAGE);
            ad.setCampaign(c);
            c.setAds(List.of(ad));
        }
        return adCampaignRepository.save(c);
    }

    private BundleSubscription givenSubscription(String bundleId, String advertiserBusinessId,
            BundleSubscriptionStatus status) {
        BundleSubscription subscription = new BundleSubscription();
        subscription.setSubscriptionId(UUID.randomUUID().toString());
        subscription.setBundleId(bundleId);
        subscription.setAdvertiserBusinessId(advertiserBusinessId);
        subscription.setCampaignId(campaign.getCampaignId().getCampaignId());
        subscription.setStripeCheckoutSessionId("cs_test_" + UUID.randomUUID());
        subscription.setStatus(status);
        subscription.setMonthlyAmount(new BigDecimal("4.00"));
        subscription.setScreenCount(1);
        return subscriptionRepository.save(subscription);
    }

    private void givenMedia(Status status, String price) {
        MediaLocation location = new MediaLocation();
        location.setName("Downtown location");
        location.setCountry("Canada");
        location.setProvince("QC");
        location.setCity("Montreal");
        location.setRegion("Montérégie");
        location.setStreet("123 Main St");
        location.setPostalCode("H1H 1H1");
        location.setLatitude(45.5017);
        location.setLongitude(-73.5673);
        location.setBusinessId(UUID.randomUUID());
        MediaLocation savedLocation = mediaLocationRepository.save(location);

        Media media = new Media();
        media.setMediaLocation(savedLocation);
        media.setTitle("Downtown board");
        media.setMediaOwnerName("Owner");
        media.setTypeOfDisplay(TypeOfDisplay.DIGITAL);
        media.setStatus(status);
        media.setVenueId("venue-gym");
        media.setPrice(new BigDecimal(price));
        media.setBusinessId(UUID.randomUUID());
        mediaRepository.save(media);
    }

    private org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec getSubscriptions(
            String forBusinessId) {
        return webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path(BASE_URI).queryParam("businessId", forBusinessId).build())
                .headers(headers -> headers.setBearerAuth(TOKEN))
                .exchange();
    }

    private org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec getLiveCampaigns(
            String mediaId) {
        return webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path(BASE_URI + "/live-campaigns")
                        .queryParam("mediaId", mediaId).build())
                .headers(headers -> headers.setBearerAuth(TOKEN))
                .exchange();
    }

    private UUID givenMediaOwnedBy(String ownerBusinessId) {
        MediaLocation location = new MediaLocation();
        location.setName("Owned location");
        location.setCountry("Canada");
        location.setProvince("QC");
        location.setCity("Montreal");
        location.setStreet("456 Owner St");
        location.setPostalCode("H2H 2H2");
        location.setLatitude(45.5);
        location.setLongitude(-73.5);
        location.setBusinessId(UUID.fromString(ownerBusinessId));
        MediaLocation savedLocation = mediaLocationRepository.save(location);

        Media media = new Media();
        media.setMediaLocation(savedLocation);
        media.setTitle("Owned board");
        media.setMediaOwnerName("Owner");
        media.setTypeOfDisplay(TypeOfDisplay.DIGITAL);
        media.setStatus(Status.ACTIVE);
        media.setPrice(new BigDecimal("4.00"));
        media.setBusinessId(UUID.fromString(ownerBusinessId));
        return mediaRepository.save(media).getId();
    }

    private void givenSubscriptionItem(String subscriptionId, UUID mediaId) {
        BundleSubscriptionItem item = new BundleSubscriptionItem();
        item.setSubscriptionId(subscriptionId);
        item.setMediaId(mediaId);
        item.setMediaOwnerBusinessId(businessId);
        item.setMonthlyAmount(new BigDecimal("4.00"));
        subscriptionItemRepository.save(item);
    }
}
