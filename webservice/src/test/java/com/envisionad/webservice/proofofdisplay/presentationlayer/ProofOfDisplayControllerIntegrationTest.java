package com.envisionad.webservice.proofofdisplay.presentationlayer;

import com.envisionad.webservice.advertisement.dataaccesslayer.AdCampaign;
import com.envisionad.webservice.advertisement.dataaccesslayer.AdCampaignIdentifier;
import com.envisionad.webservice.advertisement.dataaccesslayer.AdCampaignRepository;
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
import com.envisionad.webservice.media.DataAccessLayer.Media;
import com.envisionad.webservice.media.DataAccessLayer.MediaLocation;
import com.envisionad.webservice.media.DataAccessLayer.MediaLocationRepository;
import com.envisionad.webservice.media.DataAccessLayer.MediaRepository;
import com.envisionad.webservice.media.DataAccessLayer.Status;
import com.envisionad.webservice.media.DataAccessLayer.TypeOfDisplay;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscription;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionItem;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionItemRepository;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionRepository;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionStatus;
import com.envisionad.webservice.proofofdisplay.dataaccesslayer.ProofOfDisplayLink;
import com.envisionad.webservice.proofofdisplay.dataaccesslayer.ProofOfDisplayLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * P12's two endpoints against a real Postgres: the owner's subscription table and the link upsert,
 * including the error order that keeps a non-owner from learning how a subscription is split
 * (P12-PROGRESS D2).
 */
class ProofOfDisplayControllerIntegrationTest extends BaseIntegrationTest {

    private static final String BASE = "/api/v1/proof-of-display";

    private static final String OWNER_TOKEN = "owner-token";
    private static final String OTHER_OWNER_TOKEN = "other-owner-token";
    private static final String OWNER_USER = "auth0|owner-a";
    private static final String OTHER_OWNER_USER = "auth0|owner-b";

    private static final String OWNER_BIZ = UUID.randomUUID().toString();
    private static final String OTHER_OWNER_BIZ = UUID.randomUUID().toString();
    private static final String ADVERTISER_BIZ = UUID.randomUUID().toString();

    @Autowired private ProofOfDisplayLinkRepository linkRepository;
    @Autowired private BundleSubscriptionItemRepository itemRepository;
    @Autowired private BundleSubscriptionRepository subscriptionRepository;
    @Autowired private BundleRepository bundleRepository;
    @Autowired private AdCampaignRepository adCampaignRepository;
    @Autowired private MediaRepository mediaRepository;
    @Autowired private MediaLocationRepository mediaLocationRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private BusinessRepository businessRepository;

    private Bundle bundle;

    @BeforeEach
    void setUp() {
        linkRepository.deleteAll();
        itemRepository.deleteAll();
        subscriptionRepository.deleteAll();
        bundleRepository.deleteAll();
        businessRepository.findAll().forEach(business -> {
            business.setActiveCampaignId(null);
            businessRepository.save(business);
        });
        adCampaignRepository.deleteAll();
        mediaRepository.deleteAll();
        mediaLocationRepository.deleteAll();
        employeeRepository.deleteAll();
        businessRepository.deleteAll();

        when(jwtDecoder.decode(OWNER_TOKEN)).thenReturn(jwt(OWNER_TOKEN, OWNER_USER));
        when(jwtDecoder.decode(OTHER_OWNER_TOKEN)).thenReturn(jwt(OTHER_OWNER_TOKEN, OTHER_OWNER_USER));

        givenBusiness(OWNER_BIZ, "Screens Inc");
        givenBusiness(OTHER_OWNER_BIZ, "Other Screens");
        givenBusiness(ADVERTISER_BIZ, "Lotus Yoga");
        givenEmployee(OWNER_BIZ, OWNER_USER);
        givenEmployee(OTHER_OWNER_BIZ, OTHER_OWNER_USER);

        bundle = new Bundle();
        bundle.setNameEn("Brossard Bundle");
        bundle.setNameFr("Forfait Brossard");
        bundle.setBadgeColor("#3366FF");
        bundle.setRuleType(BundleRuleType.FULL_NETWORK);
        bundle.setActive(true);
        bundle = bundleRepository.save(bundle);
    }

    // ---------- PUT /subscriptions/{id}/link ----------

    @Test
    void putLink_bySoleOwner_createsTheLinkAndReturnsIt() {
        String subscriptionId = givenSubscription(BundleSubscriptionStatus.ACTIVE, OWNER_BIZ);

        putLink(OWNER_TOKEN, subscriptionId, "https://www.canva.com/design/abc/view")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.subscriptionId").isEqualTo(subscriptionId)
                .jsonPath("$.url").isEqualTo("https://www.canva.com/design/abc/view")
                .jsonPath("$.updatedAt").value(org.hamcrest.Matchers.matchesPattern(".*(Z|[+-]\\d{2}:\\d{2})$"));

        ProofOfDisplayLink saved = linkRepository.findBySubscriptionId(subscriptionId).orElseThrow();
        assertEquals(OWNER_USER, saved.getUpdatedByUserId());
    }

    @Test
    void putLink_again_overwritesTheSameRowAndBumpsUpdatedAt() {
        String subscriptionId = givenSubscription(BundleSubscriptionStatus.PAST_DUE, OWNER_BIZ);
        ProofOfDisplayLink existing = new ProofOfDisplayLink();
        existing.setSubscriptionId(subscriptionId);
        existing.setUrl("https://www.canva.com/old");
        existing.setUpdatedAt(LocalDateTime.now().minusDays(30));
        linkRepository.save(existing);

        putLink(OWNER_TOKEN, subscriptionId, "https://www.canva.com/new")
                .expectStatus().isOk();

        List<ProofOfDisplayLink> links = linkRepository.findAll();
        assertEquals(1, links.size(), "an edit replaces the link, it never appends one");
        assertEquals("https://www.canva.com/new", links.get(0).getUrl());
        assertTrue(links.get(0).getUpdatedAt().isAfter(LocalDateTime.now().minusMinutes(1)));
    }

    @Test
    void putLink_withAJavascriptUrl_is400AndStoresNothing() {
        String subscriptionId = givenSubscription(BundleSubscriptionStatus.ACTIVE, OWNER_BIZ);

        putLink(OWNER_TOKEN, subscriptionId, "JavaScript:alert(1)")
                .expectStatus().isBadRequest();

        assertTrue(linkRepository.findAll().isEmpty());
    }

    @Test
    void putLink_withABlankUrl_is400() {
        String subscriptionId = givenSubscription(BundleSubscriptionStatus.ACTIVE, OWNER_BIZ);

        putLink(OWNER_TOKEN, subscriptionId, " ")
                .expectStatus().isBadRequest();
    }

    @Test
    void putLink_forAnUnknownSubscription_is404() {
        putLink(OWNER_TOKEN, UUID.randomUUID().toString(), "https://www.canva.com/x")
                .expectStatus().isNotFound();
    }

    @Test
    void putLink_byABusinessOwningNoneOfTheScreens_is403() {
        String subscriptionId = givenSubscription(BundleSubscriptionStatus.ACTIVE, OWNER_BIZ);

        putLink(OTHER_OWNER_TOKEN, subscriptionId, "https://www.canva.com/x")
                .expectStatus().isForbidden();
    }

    /** D2: an outsider gets 403 even on a multi-owner subscription, never the 409 that reveals the split. */
    @Test
    void putLink_byAnOutsiderOnAMultiOwnerSubscription_is403Not409() {
        String subscriptionId = givenSubscription(BundleSubscriptionStatus.ACTIVE, OWNER_BIZ, OWNER_BIZ);
        givenItem(subscriptionId, givenMediaOwnedBy(UUID.randomUUID().toString(), "Someone else's board"));

        putLink(OTHER_OWNER_TOKEN, subscriptionId, "https://www.canva.com/x")
                .expectStatus().isForbidden();
    }

    @Test
    void putLink_byAPartOwnerOfAMultiOwnerSubscription_is409WithItsCode() {
        String subscriptionId = givenSubscription(BundleSubscriptionStatus.ACTIVE, OWNER_BIZ, OTHER_OWNER_BIZ);

        putLink(OWNER_TOKEN, subscriptionId, "https://www.canva.com/x")
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.code").isEqualTo("PROOF_LINK_MULTI_OWNER");
    }

    @Test
    void putLink_onACanceledSubscription_is409WithItsCode() {
        String subscriptionId = givenSubscription(BundleSubscriptionStatus.CANCELED, OWNER_BIZ);

        putLink(OWNER_TOKEN, subscriptionId, "https://www.canva.com/x")
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.code").isEqualTo("PROOF_LINK_NOT_EDITABLE");
    }

    @Test
    void putLink_onAnIncompleteSubscription_is409() {
        String subscriptionId = givenSubscription(BundleSubscriptionStatus.INCOMPLETE, OWNER_BIZ);

        putLink(OWNER_TOKEN, subscriptionId, "https://www.canva.com/x")
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.code").isEqualTo("PROOF_LINK_NOT_EDITABLE");
    }

    @Test
    void putLink_withoutAToken_isRejected() {
        String subscriptionId = givenSubscription(BundleSubscriptionStatus.ACTIVE, OWNER_BIZ);

        webTestClient.put().uri(BASE + "/subscriptions/{id}/link", subscriptionId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("url", "https://www.canva.com/x"))
                .exchange()
                .expectStatus().value(status -> assertTrue(status == 401 || status == 403,
                        "anonymous callers must be rejected, got " + status));
    }

    // ---------- GET /media-owner/{businessId}/subscriptions ----------

    @Test
    void ownerList_identifiesRowsByAdvertiserAndBundle_withTheActiveCampaignAndOnlyTheOwnersScreens() {
        givenActiveCampaign(ADVERTISER_BIZ, "Fall promo");
        String subscriptionId = givenSubscription(BundleSubscriptionStatus.ACTIVE, OWNER_BIZ, OTHER_OWNER_BIZ);

        getOwnerList(OWNER_TOKEN, OWNER_BIZ)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].subscriptionId").isEqualTo(subscriptionId)
                .jsonPath("$[0].subscriptionStatus").isEqualTo("ACTIVE")
                .jsonPath("$[0].advertiserBusinessName").isEqualTo("Lotus Yoga")
                .jsonPath("$[0].bundleNameEn").isEqualTo("Brossard Bundle")
                .jsonPath("$[0].bundleNameFr").isEqualTo("Forfait Brossard")
                .jsonPath("$[0].campaignName").isEqualTo("Fall promo")
                // The other owner's board is on the subscription but must not be listed.
                .jsonPath("$[0].screenNames.length()").isEqualTo(1)
                .jsonPath("$[0].screenNames[0]").isEqualTo("Board of " + OWNER_BIZ)
                .jsonPath("$[0].proofLink.status").isEqualTo("NOT_APPLICABLE")
                .jsonPath("$[0].linkUpdatedAt").doesNotExist();
    }

    @Test
    void ownerList_showsASetLinkWithItsDate_andANullCampaignWhenNoneIsSelected() {
        String subscriptionId = givenSubscription(BundleSubscriptionStatus.ACTIVE, OWNER_BIZ);
        givenLink(subscriptionId, "https://www.canva.com/proof");

        getOwnerList(OWNER_TOKEN, OWNER_BIZ)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].campaignName").doesNotExist()
                .jsonPath("$[0].proofLink.status").isEqualTo("SET")
                .jsonPath("$[0].proofLink.url").isEqualTo("https://www.canva.com/proof")
                .jsonPath("$[0].linkUpdatedAt").exists();
    }

    @Test
    void ownerList_listsRunningThenCanceled_andLeavesIncompleteOut() {
        String canceled = givenSubscription(BundleSubscriptionStatus.CANCELED, OWNER_BIZ);
        givenSubscription(BundleSubscriptionStatus.INCOMPLETE, OWNER_BIZ);
        String active = givenSubscription(BundleSubscriptionStatus.ACTIVE, OWNER_BIZ);

        getOwnerList(OWNER_TOKEN, OWNER_BIZ)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[0].subscriptionId").isEqualTo(active)
                .jsonPath("$[1].subscriptionId").isEqualTo(canceled)
                .jsonPath("$[0].proofLink.status").isEqualTo("NOT_SET");
    }

    @Test
    void ownerList_isEmptyForAnOwnerWithNoSubscriptions() {
        givenSubscription(BundleSubscriptionStatus.ACTIVE, OTHER_OWNER_BIZ);

        getOwnerList(OWNER_TOKEN, OWNER_BIZ)
                .expectStatus().isOk()
                .expectBody().jsonPath("$.length()").isEqualTo(0);
    }

    @Test
    void ownerList_forAnotherBusiness_is403() {
        getOwnerList(OWNER_TOKEN, OTHER_OWNER_BIZ)
                .expectStatus().isForbidden();
    }

    // ---------- fixtures ----------

    private static Jwt jwt(String token, String userId) {
        return Jwt.withTokenValue(token).header("alg", "none").claim("sub", userId).build();
    }

    private WebTestClient.ResponseSpec putLink(String token, String subscriptionId, String url) {
        return webTestClient.put().uri(BASE + "/subscriptions/{id}/link", subscriptionId)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("url", url))
                .exchange();
    }

    private WebTestClient.ResponseSpec getOwnerList(String token, String businessId) {
        return webTestClient.get().uri(BASE + "/media-owner/{businessId}/subscriptions", businessId)
                .headers(headers -> headers.setBearerAuth(token))
                .exchange();
    }

    private void givenBusiness(String businessId, String name) {
        Business business = new Business();
        business.setBusinessId(new BusinessIdentifier(businessId));
        business.setName(name);
        businessRepository.save(business);
    }

    private void givenEmployee(String businessId, String userId) {
        Employee employee = new Employee();
        employee.setEmployeeId(new EmployeeIdentifier());
        employee.setBusinessId(new BusinessIdentifier(businessId));
        employee.setUserId(userId);
        employeeRepository.save(employee);
    }

    private void givenActiveCampaign(String businessId, String name) {
        AdCampaign campaign = new AdCampaign();
        campaign.setCampaignId(new AdCampaignIdentifier());
        campaign.setBusinessId(new BusinessIdentifier(businessId));
        campaign.setName(name);
        campaign = adCampaignRepository.save(campaign);
        String campaignId = campaign.getCampaignId().getCampaignId();

        Business business = businessRepository.findByBusinessId_BusinessId(businessId);
        business.setActiveCampaignId(campaignId);
        businessRepository.save(business);
    }

    /** A subscription from the advertiser with one screen per listed owner. Returns its id. */
    private String givenSubscription(BundleSubscriptionStatus status, String... ownerBusinessIds) {
        BundleSubscription subscription = new BundleSubscription();
        String subscriptionId = UUID.randomUUID().toString();
        subscription.setSubscriptionId(subscriptionId);
        subscription.setBundleId(bundle.getBundleId());
        subscription.setAdvertiserBusinessId(ADVERTISER_BIZ);
        subscription.setStripeCheckoutSessionId("cs_test_" + UUID.randomUUID());
        subscription.setStatus(status);
        subscription.setMonthlyAmount(new BigDecimal("4.00"));
        subscription.setScreenCount(ownerBusinessIds.length);
        subscriptionRepository.save(subscription);

        for (String owner : ownerBusinessIds) {
            givenItem(subscriptionId, givenMediaOwnedBy(owner, "Board of " + owner));
        }
        return subscriptionId;
    }

    private void givenLink(String subscriptionId, String url) {
        ProofOfDisplayLink link = new ProofOfDisplayLink();
        link.setSubscriptionId(subscriptionId);
        link.setUrl(url);
        link.setUpdatedAt(LocalDateTime.now());
        linkRepository.save(link);
    }

    private Media givenMediaOwnedBy(String ownerBusinessId, String title) {
        MediaLocation location = new MediaLocation();
        location.setName("Location");
        location.setCountry("Canada");
        location.setProvince("QC");
        location.setCity("Brossard");
        location.setStreet("1 Main St");
        location.setPostalCode("J4W 1A1");
        location.setLatitude(45.45);
        location.setLongitude(-73.46);
        location.setBusinessId(UUID.fromString(ownerBusinessId));
        MediaLocation savedLocation = mediaLocationRepository.save(location);

        Media media = new Media();
        media.setMediaLocation(savedLocation);
        media.setTitle(title);
        media.setMediaOwnerName("Owner");
        media.setTypeOfDisplay(TypeOfDisplay.DIGITAL);
        media.setStatus(Status.ACTIVE);
        media.setPrice(new BigDecimal("4.00"));
        media.setBusinessId(UUID.fromString(ownerBusinessId));
        return mediaRepository.save(media);
    }

    private void givenItem(String subscriptionId, Media media) {
        BundleSubscriptionItem item = new BundleSubscriptionItem();
        item.setSubscriptionId(subscriptionId);
        item.setMediaId(media.getId());
        item.setMediaOwnerBusinessId(media.getBusinessId().toString());
        item.setMonthlyAmount(new BigDecimal("4.00"));
        itemRepository.save(item);
    }
}
