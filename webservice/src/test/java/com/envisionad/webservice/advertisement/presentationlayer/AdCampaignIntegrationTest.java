package com.envisionad.webservice.advertisement.presentationlayer;

import com.envisionad.webservice.advertisement.dataaccesslayer.*;
import com.envisionad.webservice.advertisement.presentationlayer.models.AdCampaignRequestModel;
import com.envisionad.webservice.advertisement.presentationlayer.models.AdRequestModel;
import com.envisionad.webservice.advertisement.presentationlayer.models.AdVenueTagsRequestModel;
import com.envisionad.webservice.business.dataaccesslayer.*;
import com.envisionad.webservice.venue.dataaccesslayer.Venue;
import com.envisionad.webservice.venue.dataaccesslayer.VenueRepository;
import com.envisionad.webservice.config.BaseIntegrationTest;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscription;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionRepository;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

public class AdCampaignIntegrationTest extends BaseIntegrationTest {
    private final String BASE_URI_AD_CAMPAIGNS = "/api/v1/businesses/{businessId}/campaigns";

    @Autowired
    private AdCampaignRepository adCampaignRepository;

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    private BusinessIdentifier businessId;
    private static final String TEST_USER_ID = "auth0|696a88eb347945897ef17093";

    private static final String BUSINESS_ID = "b0eebc99-9c0b-4ef8-bb6d-6bb9bd380b22";
    @Autowired
    private BundleSubscriptionRepository bundleSubscriptionRepository;

    @Autowired
    private VenueRepository venueRepository;

    @BeforeEach
    void setUp() {
        // Clear all data from previous tests to avoid constraint violations
        bundleSubscriptionRepository.deleteAll();
        adCampaignRepository.deleteAll();
        // Venues must go after campaigns: ad_venue_tags FKs both sides, and under
        // ddl-auto: create those FKs have no cascade.
        venueRepository.deleteAll();
        employeeRepository.deleteAll();
        businessRepository.deleteAll();

        Jwt media = Jwt.withTokenValue("media-token")
                .header("alg", "none")
                .claim("sub", "auth0|696a89137cfdb558ea4a4a4a")
                .claim("scope", "read write")
                .claim("permissions", List.of(
                        "create:media",
                        "update:media",
                        "update:business",
                        "read:employee",
                        "create:employee",
                        "delete:employee",
                        "read:verification",
                        "create:verification"
                ))
                .build();

        Jwt advertiser = Jwt.withTokenValue("advertiser-token")
                .header("alg", "none")
                .claim("sub", "auth0|696a88eb347945897ef17093")
                .claim("scope", "read write")
                .claim("permissions", List.of(
                        "readAll:campaign",
                        "read:campaign",
                        "create:campaign",
                        "update:campaign",
                        "update:business",
                        "read:employee",
                        "create:employee",
                        "delete:employee",
                        "read:verification",
                        "create:verification",
                        "delete:campaign"
                ))
                .build();

        Jwt newUser = Jwt.withTokenValue("newUser-token")
                .header("alg", "none")
                .claim("sub", "auth0|696b10a00bba0a28c21d3829")
                .claim("scope", "read write")
                .build();

        when(jwtDecoder.decode("media-token")).thenReturn(media);
        when(jwtDecoder.decode("advertiser-token")).thenReturn(advertiser);
        when(jwtDecoder.decode("newUser-token")).thenReturn(newUser);

        // Initialize businessId for all tests
        businessId = new BusinessIdentifier(BUSINESS_ID);

        // Create a test business
        Business business = new Business();
        business.setBusinessId(businessId);
        business.setName("Test Business");
        business.setOwnerId(TEST_USER_ID);
        business.setOrganizationSize(OrganizationSize.LARGE);
        business.setVerified(true);
        Address address = createTestAddress();
        business.setAddress(address);
        Roles roles = new Roles();
        roles.setAdvertiser(true);
        business.setRoles(roles);
        businessRepository.save(business);

        // Create an employee for the test user
        Employee employee = new Employee();
        employee.setEmployeeId(new EmployeeIdentifier());
        employee.setBusinessId(businessId);
        employee.setUserId(TEST_USER_ID);
        employeeRepository.save(employee);
    }

    private Address createTestAddress() {
        Address address = new Address();
        address.setCity("St-Lambert");
        address.setStreet("900 Riverside");
        address.setCountry("Canada");
        address.setState("QC");
        address.setZipCode("J4P 3P2");
        return address;
    }


    @Test
    void getAllBusinessCampaigns_shouldReturnAllBusinessCampaigns() {
        // Arrange - Create test campaigns for a business
        AdCampaign campaign1 = new AdCampaign();
        campaign1.setName("Winter Sale");
        campaign1.setCampaignId(new AdCampaignIdentifier());
        campaign1.setBusinessId(businessId);

        AdCampaign campaign2 = new AdCampaign();
        campaign2.setName("Summer Sale");
        campaign2.setCampaignId(new AdCampaignIdentifier());
        campaign2.setBusinessId(businessId);

        adCampaignRepository.save(campaign1);
        adCampaignRepository.save(campaign2);

        // Act & Assert
        webTestClient.get()
                .uri(BASE_URI_AD_CAMPAIGNS, businessId.getBusinessId())
                .headers(headers -> headers.setBearerAuth("advertiser-token"))
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Object.class)
                .hasSize(2);
    }

    @Test
    void createAdCampaign_shouldCreateNewCampaign() {
        // Arrange
        AdCampaignRequestModel requestModel = new AdCampaignRequestModel();
        requestModel.setName("Summer Sale");

        // Act & Assert
        webTestClient.post()
                .uri(BASE_URI_AD_CAMPAIGNS, businessId.getBusinessId())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth("advertiser-token"))
                .bodyValue(requestModel)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.name").isEqualTo("Summer Sale")
                .jsonPath("$.campaignId").isNotEmpty();

        // Assert
        assertEquals(1, adCampaignRepository.count());
    }

    @Test
    void addAdToCampaign_shouldAddAdSuccessfully() {
        // Arrange

        AdCampaign adCampaign = new AdCampaign();
        adCampaign.setName("Winter Sale");
        adCampaign.setCampaignId(new AdCampaignIdentifier());
        adCampaign.setBusinessId(businessId);

        AdCampaign savedCampaign = adCampaignRepository.save(adCampaign);
        String campaignId = savedCampaign.getCampaignId().getCampaignId();

        AdRequestModel adRequestModel = new AdRequestModel();
        adRequestModel.setName("Summer Beach Banner");
        adRequestModel.setAdUrl("https://cdn.envisionad.com/summer-beach.jpg");
        adRequestModel.setAdType("IMAGE");

        // Act & Assert
        webTestClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path(BASE_URI_AD_CAMPAIGNS + "/{campaignId}/ads")
                        .build(businessId.getBusinessId(), campaignId))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth("advertiser-token"))
                .bodyValue(adRequestModel)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.name").isEqualTo("Summer Beach Banner")
                .jsonPath("$.adUrl").isEqualTo("https://cdn.envisionad.com/summer-beach.jpg")
                .jsonPath("$.adType").isEqualTo("IMAGE")
                .jsonPath("$.adId").isNotEmpty();
        // Assert
        assertEquals(1, adCampaignRepository.count());
        AdCampaign updatedCampaign = adCampaignRepository.findByCampaignIdWithAds(savedCampaign.getCampaignId().getCampaignId());
        assertNotNull(updatedCampaign.getAds());
        assertEquals(1, updatedCampaign.getAds().size());
    }

    @Test
    void createAdCampaign_shouldPersistCampaign() {
        // Arrange
        AdCampaignRequestModel requestModel = new AdCampaignRequestModel();
        requestModel.setName("Spring Sale");

        // Act
        webTestClient.post()
                .uri(BASE_URI_AD_CAMPAIGNS, businessId.getBusinessId())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth("advertiser-token"))
                .bodyValue(requestModel)
                .exchange()
                .expectStatus().isCreated();

        // Assert
        assertEquals(1, adCampaignRepository.count());
        AdCampaign savedCampaign = adCampaignRepository.findAll().getFirst();
        assertEquals("Spring Sale", savedCampaign.getName());
        assertNotNull(savedCampaign.getCampaignId());
        assertNotNull(savedCampaign.getCampaignId().getCampaignId());
    }

    @Test
    void deleteAdFromCampaign_shouldDeleteAdSuccessfully() {

        AdCampaign adCampaign = new AdCampaign();
        adCampaign.setName("Winter Sale");
        adCampaign.setCampaignId(new AdCampaignIdentifier());
        adCampaign.setBusinessId(businessId);

        Ad ad = new Ad();
        ad.setName("Winter Discount");
        ad.setAdUrl("http://example.com/winter-discount");
        ad.setAdType(AdType.IMAGE);
        ad.setAdIdentifier(new AdIdentifier());

        // IMPORTANT: set both sides of the relationship before save
        ad.setCampaign(adCampaign);
        adCampaign.getAds().add(ad);

        AdCampaign savedCampaign = adCampaignRepository.save(adCampaign);
        String campaignId = savedCampaign.getCampaignId().getCampaignId();
        String adId = ad.getAdIdentifier().getAdIdentifier();

        webTestClient.delete()
                .uri(uriBuilder -> uriBuilder
                        .path(BASE_URI_AD_CAMPAIGNS + "/{campaignId}/ads/{adId}")
                        .build(businessId.getBusinessId(), campaignId, adId))
                .headers(headers -> headers.setBearerAuth("advertiser-token"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("Winter Discount")
                .jsonPath("$.adUrl").isEqualTo("http://example.com/winter-discount")
                .jsonPath("$.adType").isEqualTo("IMAGE")
                .jsonPath("$.adId").isEqualTo(adId);

        AdCampaign updatedCampaign = adCampaignRepository.findByCampaignIdWithAds(campaignId);
        assertNotNull(updatedCampaign.getAds());
        assertEquals(0, updatedCampaign.getAds().size());
    }


    //    ============ NEGATIVE TESTS ============
    @Test
    void addAdToCampaign_shouldThrowInvalidAdTypeException_whenTypeIsInvalid() {
        // Arrange

        AdCampaign adCampaign = new AdCampaign();
        adCampaign.setName("Winter Sale");
        adCampaign.setCampaignId(new AdCampaignIdentifier());
        adCampaign.setBusinessId(businessId);
        AdCampaign savedCampaign = adCampaignRepository.save(adCampaign);
        String campaignId = savedCampaign.getCampaignId().getCampaignId();

        AdRequestModel adRequestModel = new AdRequestModel();
        adRequestModel.setName("Invalid Type Ad");
        adRequestModel.setAdUrl("https://cdn.envisionad.com/img.jpg");
        // ACT: Set an invalid Type string to trigger IllegalArgumentException in the service
        adRequestModel.setAdType("NON_EXISTENT_TYPE");

        // Act & Assert
        webTestClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path(BASE_URI_AD_CAMPAIGNS + "/{campaignId}/ads")
                        .build(businessId.getBusinessId(), campaignId))
                .headers(headers -> headers.setBearerAuth("advertiser-token"))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue(adRequestModel)
                .exchange()
                // EXPECTATION: Depends on your GlobalExceptionHandler.
                // Usually 422 (Unprocessable Entity) or 400 (Bad Request).
                .expectStatus().is4xxClientError()
                .expectBody();
    }

    @Test
    void addAdToCampaign_shouldThrowNotFound_whenCampaignDoesNotExist() {
        // Arrange
        String nonExistentId = "999-invalid-id";

        AdRequestModel adRequestModel = new AdRequestModel();
        adRequestModel.setName("Orphan Ad");
        adRequestModel.setAdUrl("https://cdn.envisionad.com/img.jpg");
        adRequestModel.setAdType("IMAGE");

        // Act & Assert
        webTestClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path(BASE_URI_AD_CAMPAIGNS + "/{campaignId}/ads")
                        .build(businessId.getBusinessId(), nonExistentId))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth("advertiser-token"))
                .bodyValue(adRequestModel)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void deleteAdFromNonExistingCampaign_shouldReturnCampaignNotFound() {
        // Arrange
        String nonExistentCampaignId = "non-existent-campaign-id";
        String adId = UUID.randomUUID().toString();


        // Act & Assert
        webTestClient.delete()
                .uri(uriBuilder -> uriBuilder
                        .path(BASE_URI_AD_CAMPAIGNS + "/{campaignId}/ads/{adId}")
                        .build(businessId.getBusinessId(), nonExistentCampaignId, adId))
                .headers(headers -> headers.setBearerAuth("advertiser-token"))
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void deleteNonExistingAdFromCampaign_shouldReturnAdNotFound() {
        // Arrange

        AdCampaign adCampaign = new AdCampaign();
        adCampaign.setName("Winter Sale");
        adCampaign.setCampaignId(new AdCampaignIdentifier());
        adCampaign.setBusinessId(businessId);
        AdCampaign savedCampaign = adCampaignRepository.save(adCampaign);
        String campaignId = savedCampaign.getCampaignId().getCampaignId();
        String nonExistentAdId = "non-existent-ad-id";

        // Act & Assert
        webTestClient.delete()
                .uri(uriBuilder -> uriBuilder
                        .path(BASE_URI_AD_CAMPAIGNS + "/{campaignId}/ads/{adId}")
                        .build(businessId.getBusinessId(), campaignId, nonExistentAdId))
                .headers(headers -> headers.setBearerAuth("advertiser-token"))
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void deleteCampaign_notTiedToAnySubscription_shouldDeleteSuccessfully() {
        // Arrange
        AdCampaign adCampaign = new AdCampaign();
        adCampaign.setName("Winter Sale");
        adCampaign.setCampaignId(new AdCampaignIdentifier());
        adCampaign.setBusinessId(businessId);
        AdCampaign savedCampaign = adCampaignRepository.save(adCampaign);
        String campaignId = savedCampaign.getCampaignId().getCampaignId();

        // Act & Assert
        webTestClient.delete()
                .uri(uriBuilder -> uriBuilder
                        .path(BASE_URI_AD_CAMPAIGNS + "/{campaignId}")
                        .build(businessId.getBusinessId(), campaignId))
                .headers(headers -> headers.setBearerAuth("advertiser-token"))
                .exchange()
                .expectStatus().isOk();

        assertEquals(0, adCampaignRepository.count());
    }

    /**
     * P6 follow-up, behaviour change 2: {@code bundle_subscriptions.campaign_id} and its
     * {@code ON DELETE RESTRICT} FK are gone, and the campaign-delete guard no longer mirrors
     * them (D42/D47 reversed). A campaign that is not the business's {@code active_campaign_id}
     * is deletable regardless of subscription history — live, past-due, cancelled, or abandoned.
     * Previously any subscription that had ever referenced it froze it forever.
     */
    @ParameterizedTest
    @EnumSource(BundleSubscriptionStatus.class)
    void deleteCampaign_withSubscriptionsButNotTheActiveCampaign_nowDeletes(
            BundleSubscriptionStatus status) {
        String campaignId = persistCampaign("Disposable " + status);
        persistSubscription(campaignId, status);

        expectDeleteStatus(campaignId, 200);

        assertNull(adCampaignRepository.findByCampaignId_CampaignId(campaignId));
    }

    @Test
    void deleteCampaign_whenActiveAndSubscriptionIsLive_returnsConflict() {
        String campaignId = persistCampaign("Currently Displaying");
        Business business = businessRepository.findByBusinessId_BusinessId(BUSINESS_ID);
        business.setActiveCampaignId(campaignId);
        businessRepository.saveAndFlush(business);
        persistSubscription(campaignId, BundleSubscriptionStatus.ACTIVE);

        webTestClient.delete()
                .uri(uriBuilder -> uriBuilder
                        .path(BASE_URI_AD_CAMPAIGNS + "/{campaignId}")
                        .build(BUSINESS_ID, campaignId))
                .headers(headers -> headers.setBearerAuth("advertiser-token"))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.message").value(message ->
                        org.junit.jupiter.api.Assertions.assertTrue(
                                message.toString().contains("active campaign")));

        assertNotNull(adCampaignRepository.findByCampaignId_CampaignId(campaignId));
    }

    /**
     * P6 FR-3.1: the active-campaign delete block is unconditional — no live subscription
     * required, and the pointer is not cleared to let the delete through.
     */
    @Test
    void deleteCampaign_whenActiveButNoSubscription_stillReturnsConflict() {
        String campaignId = persistCampaign("Idle Active Campaign");
        Business business = businessRepository.findByBusinessId_BusinessId(BUSINESS_ID);
        business.setActiveCampaignId(campaignId);
        businessRepository.saveAndFlush(business);

        expectDeleteStatus(campaignId, 409);

        assertNotNull(adCampaignRepository.findByCampaignId_CampaignId(campaignId));
        assertEquals(campaignId,
                businessRepository.findByBusinessId_BusinessId(BUSINESS_ID).getActiveCampaignId());
    }

    @Test
    void deleteFinalCreative_whenCampaignIsActiveAndSubscriptionIsLive_returnsConflict() {
        String campaignId = persistCampaign("One Creative Live");
        String adId = createAdAndGetId(campaignId, adRequest("Only Creative", List.of()));
        Business business = businessRepository.findByBusinessId_BusinessId(BUSINESS_ID);
        business.setActiveCampaignId(campaignId);
        businessRepository.saveAndFlush(business);
        persistSubscription(campaignId, BundleSubscriptionStatus.ACTIVE);

        webTestClient.delete()
                .uri(uriBuilder -> uriBuilder
                        .path(BASE_URI_AD_CAMPAIGNS + "/{campaignId}/ads/{adId}")
                        .build(BUSINESS_ID, campaignId, adId))
                .headers(headers -> headers.setBearerAuth("advertiser-token"))
                .exchange()
                .expectStatus().isEqualTo(409);

        assertEquals(1, adCampaignRepository.findByCampaignIdWithAds(campaignId).getAds().size());
    }

    @Test
    void addCreative_toCampaignOwnedByAnotherBusiness_returnsForbidden() {
        String campaignId = persistCampaign(
                "Another Business Campaign", new BusinessIdentifier("other-business"));

        postAd(campaignId, adRequest("Unauthorized Creative", List.of()))
                .expectStatus().isForbidden();

        assertEquals(0, adCampaignRepository.findByCampaignIdWithAds(campaignId).getAds().size());
    }

    @Test
    void deleteCreative_fromCampaignOwnedByAnotherBusiness_returnsForbidden() {
        String campaignId = persistCampaign(
                "Another Business Campaign", new BusinessIdentifier("other-business"));
        String adId = createAdAndGetId(campaignId, adRequest("Protected Creative", List.of()));

        webTestClient.delete()
                .uri(uriBuilder -> uriBuilder
                        .path(BASE_URI_AD_CAMPAIGNS + "/{campaignId}/ads/{adId}")
                        .build(BUSINESS_ID, campaignId, adId))
                .headers(headers -> headers.setBearerAuth("advertiser-token"))
                .exchange()
                .expectStatus().isForbidden();

        assertEquals(1, adCampaignRepository.findByCampaignIdWithAds(campaignId).getAds().size());
    }

    private String persistCampaign(String name) {
        AdCampaign adCampaign = new AdCampaign();
        adCampaign.setName(name);
        adCampaign.setCampaignId(new AdCampaignIdentifier());
        adCampaign.setBusinessId(businessId);
        return adCampaignRepository.save(adCampaign).getCampaignId().getCampaignId();
    }

    private void persistSubscription(String campaignId, BundleSubscriptionStatus status) {
        BundleSubscription subscription = new BundleSubscription();
        subscription.setSubscriptionId(UUID.randomUUID().toString());
        subscription.setBundleId(UUID.randomUUID().toString());
        subscription.setAdvertiserBusinessId(businessId.getBusinessId());
        subscription.setStripeCheckoutSessionId("cs_test_" + UUID.randomUUID());
        subscription.setStatus(status);
        subscription.setMonthlyAmount(new java.math.BigDecimal("48.00"));
        subscription.setScreenCount(15);
        bundleSubscriptionRepository.save(subscription);
    }

    private void expectDeleteStatus(String campaignId, int expectedStatus) {
        webTestClient.delete()
                .uri(uriBuilder -> uriBuilder
                        .path(BASE_URI_AD_CAMPAIGNS + "/{campaignId}")
                        .build(businessId.getBusinessId(), campaignId))
                .headers(headers -> headers.setBearerAuth("advertiser-token"))
                .exchange()
                .expectStatus().isEqualTo(expectedStatus);
    }

    // ---------------- P7: venue tags ----------------

    @Test
    void addAdToCampaign_withVenueIds_returns201WithVenueIds() {
        String campaignId = persistCampaign("Tagged Campaign", businessId);
        Venue gym = persistVenue("Gym", "Gymnase");

        postAd(campaignId, adRequest("Gym Banner", List.of(gym.getVenueId())))
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.venueIds").isArray()
                .jsonPath("$.venueIds.length()").isEqualTo(1)
                .jsonPath("$.venueIds[0]").isEqualTo(gym.getVenueId());
    }

    @Test
    void addAdToCampaign_withoutVenueIds_returnsEmptyVenueIdsArray() {
        String campaignId = persistCampaign("Untagged Campaign", businessId);

        // Must serialize as [], never null — the frontend types venueIds as required.
        postAd(campaignId, adRequest("Universal Banner", null))
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.venueIds").isArray()
                .jsonPath("$.venueIds.length()").isEqualTo(0);
    }

    @Test
    void addAdToCampaign_withUnknownVenueId_returns404AndPersistsNoAd() {
        String campaignId = persistCampaign("Ghost Venue Campaign", businessId);

        postAd(campaignId, adRequest("Doomed Banner", List.of("venue-does-not-exist")))
                .expectStatus().isNotFound();

        assertEquals(0, adCampaignRepository.findByCampaignIdWithAds(campaignId).getAds().size());
    }

    @Test
    void addAdToCampaign_withDuplicateVenueIds_persistsOneTagPerVenue() {
        String campaignId = persistCampaign("Dupe Campaign", businessId);
        Venue gym = persistVenue("Gym", "Gymnase");

        postAd(campaignId, adRequest("Gym Banner", List.of(gym.getVenueId(), gym.getVenueId())))
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.venueIds.length()").isEqualTo(1);
    }

    @Test
    void updateAdVenueTags_replacesTags_returns200() {
        Venue gym = persistVenue("Gym", "Gymnase");
        Venue barber = persistVenue("Barbershop", "Salon de coiffure");
        String campaignId = persistCampaign("Swap Tags", businessId);
        String adId = createAdAndGetId(campaignId, adRequest("Banner", List.of(gym.getVenueId())));

        putVenueTags(businessId.getBusinessId(), campaignId, adId, List.of(barber.getVenueId()))
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.venueIds.length()").isEqualTo(1)
                .jsonPath("$.venueIds[0]").isEqualTo(barber.getVenueId());
    }

    @Test
    void updateAdVenueTags_withEmptyList_clearsTags() {
        Venue gym = persistVenue("Gym", "Gymnase");
        String campaignId = persistCampaign("Clear Tags", businessId);
        String adId = createAdAndGetId(campaignId, adRequest("Banner", List.of(gym.getVenueId())));

        putVenueTags(businessId.getBusinessId(), campaignId, adId, List.of())
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.venueIds.length()").isEqualTo(0);
    }

    @Test
    void updateAdVenueTags_unknownCampaign_returns404() {
        putVenueTags(businessId.getBusinessId(), UUID.randomUUID().toString(), "some-ad", List.of())
                .expectStatus().isNotFound();
    }

    @Test
    void updateAdVenueTags_unknownAd_returns404() {
        String campaignId = persistCampaign("No Such Ad", businessId);

        putVenueTags(businessId.getBusinessId(), campaignId, "ad-does-not-exist", List.of())
                .expectStatus().isNotFound();
    }

    @Test
    void updateAdVenueTags_unknownVenue_returns404() {
        String campaignId = persistCampaign("Ghost Tag", businessId);
        String adId = createAdAndGetId(campaignId, adRequest("Banner", null));

        putVenueTags(businessId.getBusinessId(), campaignId, adId, List.of("venue-does-not-exist"))
                .expectStatus().isNotFound();
    }

    @Test
    void updateAdVenueTags_campaignOfAnotherBusiness_returns403() {
        // Caller IS an employee of the path business, but the campaign belongs elsewhere.
        BusinessIdentifier otherBusiness = new BusinessIdentifier(UUID.randomUUID().toString());
        String campaignId = persistCampaign("Someone Else's Campaign", otherBusiness);
        String adId = createAdAndGetId(campaignId, adRequest("Banner", null));

        putVenueTags(businessId.getBusinessId(), campaignId, adId, List.of())
                .expectStatus().isForbidden();
    }

    @Test
    void updateAdVenueTags_callerNotEmployeeOfBusiness_returns403() {
        String campaignId = persistCampaign("Guarded", businessId);
        String adId = createAdAndGetId(campaignId, adRequest("Banner", null));

        putVenueTags(UUID.randomUUID().toString(), campaignId, adId, List.of())
                .expectStatus().isForbidden();
    }

    @Test
    void updateAdVenueTags_withoutUpdateCampaignPermission_returns403() {
        String campaignId = persistCampaign("Perm Guarded", businessId);
        String adId = createAdAndGetId(campaignId, adRequest("Banner", null));

        webTestClient.put()
                .uri(uriBuilder -> uriBuilder
                        .path(BASE_URI_AD_CAMPAIGNS + "/{campaignId}/ads/{adId}/venue-tags")
                        .build(businessId.getBusinessId(), campaignId, adId))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth("media-token"))
                .bodyValue(venueTagsRequest(List.of()))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void getAllBusinessCampaigns_includesVenueIdsOnEachAd() {
        Venue gym = persistVenue("Gym", "Gymnase");
        String campaignId = persistCampaign("Listed", businessId);
        createAdAndGetId(campaignId, adRequest("Banner", List.of(gym.getVenueId())));

        webTestClient.get()
                .uri(BASE_URI_AD_CAMPAIGNS, businessId.getBusinessId())
                .headers(headers -> headers.setBearerAuth("advertiser-token"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].ads[0].venueIds[0]").isEqualTo(gym.getVenueId());
    }

    @Test
    void deleteAdFromCampaign_taggedAd_returns200WithVenueIds() {
        // Regression guard: the response is mapped before the ad is detached, so reading
        // the lazy venues collection still works.
        Venue gym = persistVenue("Gym", "Gymnase");
        String campaignId = persistCampaign("Delete Tagged", businessId);
        String adId = createAdAndGetId(campaignId, adRequest("Banner", List.of(gym.getVenueId())));

        webTestClient.delete()
                .uri(uriBuilder -> uriBuilder
                        .path(BASE_URI_AD_CAMPAIGNS + "/{campaignId}/ads/{adId}")
                        .build(businessId.getBusinessId(), campaignId, adId))
                .headers(headers -> headers.setBearerAuth("advertiser-token"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.venueIds[0]").isEqualTo(gym.getVenueId());
    }

    // ---------------- P7 helpers ----------------

    private Venue persistVenue(String nameEn, String nameFr) {
        Venue venue = new Venue();
        venue.setVenueId(UUID.randomUUID().toString());
        venue.setNameEn(nameEn);
        venue.setNameFr(nameFr);
        venue.setColorCode("#FF5733");
        return venueRepository.save(venue);
    }

    private String persistCampaign(String name, BusinessIdentifier owner) {
        AdCampaign campaign = new AdCampaign();
        campaign.setName(name);
        campaign.setCampaignId(new AdCampaignIdentifier());
        campaign.setBusinessId(owner);
        return adCampaignRepository.save(campaign).getCampaignId().getCampaignId();
    }

    private static AdRequestModel adRequest(String name, List<String> venueIds) {
        AdRequestModel request = new AdRequestModel();
        request.setName(name);
        request.setAdUrl("https://cdn.envisionad.com/" + name.replace(' ', '-') + ".jpg");
        request.setAdType("IMAGE");
        request.setVenueIds(venueIds);
        return request;
    }

    private static AdVenueTagsRequestModel venueTagsRequest(List<String> venueIds) {
        AdVenueTagsRequestModel request = new AdVenueTagsRequestModel();
        request.setVenueIds(venueIds);
        return request;
    }

    private org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec postAd(
            String campaignId, AdRequestModel request) {
        return webTestClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path(BASE_URI_AD_CAMPAIGNS + "/{campaignId}/ads")
                        .build(businessId.getBusinessId(), campaignId))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth("advertiser-token"))
                .bodyValue(request)
                .exchange();
    }

    private org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec putVenueTags(
            String pathBusinessId, String campaignId, String adId, List<String> venueIds) {
        return webTestClient.put()
                .uri(uriBuilder -> uriBuilder
                        .path(BASE_URI_AD_CAMPAIGNS + "/{campaignId}/ads/{adId}/venue-tags")
                        .build(pathBusinessId, campaignId, adId))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth("advertiser-token"))
                .bodyValue(venueTagsRequest(venueIds))
                .exchange();
    }

    /** Persists an ad directly so a test can act on it without going through the API. */
    private String createAdAndGetId(String campaignId, AdRequestModel request) {
        AdCampaign campaign = adCampaignRepository.findByCampaignIdWithAds(campaignId);

        Ad ad = new Ad();
        ad.setAdIdentifier(new AdIdentifier());
        ad.setName(request.getName());
        ad.setAdUrl(request.getAdUrl());
        ad.setAdType(AdType.valueOf(request.getAdType()));
        ad.setCampaign(campaign);
        if (request.getVenueIds() != null) {
            ad.setVenues(request.getVenueIds().stream()
                    .map(id -> venueRepository.findByVenueId(id).orElseThrow())
                    .toList());
        }

        campaign.getAds().add(ad);
        adCampaignRepository.save(campaign);

        return ad.getAdIdentifier().getAdIdentifier();
    }
}
