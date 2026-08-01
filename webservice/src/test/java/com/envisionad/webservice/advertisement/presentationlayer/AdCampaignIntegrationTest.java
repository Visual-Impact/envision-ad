package com.envisionad.webservice.advertisement.presentationlayer;

import com.envisionad.webservice.advertisement.dataaccesslayer.*;
import com.envisionad.webservice.advertisement.presentationlayer.models.AdCampaignRequestModel;
import com.envisionad.webservice.advertisement.presentationlayer.models.AdRequestModel;
import com.envisionad.webservice.business.dataaccesslayer.*;
import com.envisionad.webservice.config.BaseIntegrationTest;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscription;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionRepository;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    @BeforeEach
    void setUp() {
        // Clear all data from previous tests to avoid constraint violations
        bundleSubscriptionRepository.deleteAll();
        adCampaignRepository.deleteAll();
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
     * D42: the delete guard survives the reservation retirement, re-pointed at live bundle
     * subscriptions. ACTIVE blocks the delete.
     */
    @Test
    void deleteCampaign_tiedToActiveSubscription_shouldReturnConflict() {
        String campaignId = persistCampaign("Winter Sale");
        persistSubscription(campaignId, BundleSubscriptionStatus.ACTIVE);

        expectDeleteStatus(campaignId, 409);
    }

    /** A failed payment does not release the campaign — PAST_DUE is still live. */
    @Test
    void deleteCampaign_tiedToPastDueSubscription_shouldReturnConflict() {
        String campaignId = persistCampaign("Winter Sale");
        persistSubscription(campaignId, BundleSubscriptionStatus.PAST_DUE);

        expectDeleteStatus(campaignId, 409);
    }

    /**
     * The bundle-era analogue of the old "expired reservation" case: once the subscription is
     * CANCELED the campaign is free to delete again.
     */
    @Test
    void deleteCampaign_tiedToCanceledSubscriptionOnly_shouldSucceed() {
        String campaignId = persistCampaign("Summer Clearance");
        persistSubscription(campaignId, BundleSubscriptionStatus.CANCELED);

        webTestClient.delete()
                .uri(uriBuilder -> uriBuilder
                        .path(BASE_URI_AD_CAMPAIGNS + "/{campaignId}")
                        .build(businessId.getBusinessId(), campaignId))
                .headers(headers -> headers.setBearerAuth("advertiser-token"))
                .exchange()
                .expectStatus().is2xxSuccessful();
    }

    /**
     * An INCOMPLETE row is an abandoned checkout, not a live subscription, so it must not lock
     * the campaign. This is the case the trigger and the service guard both scope to
     * ACTIVE/PAST_DUE precisely to avoid.
     */
    @Test
    void deleteCampaign_tiedToIncompleteSubscriptionOnly_shouldSucceed() {
        String campaignId = persistCampaign("Abandoned Checkout");
        persistSubscription(campaignId, BundleSubscriptionStatus.INCOMPLETE);

        webTestClient.delete()
                .uri(uriBuilder -> uriBuilder
                        .path(BASE_URI_AD_CAMPAIGNS + "/{campaignId}")
                        .build(businessId.getBusinessId(), campaignId))
                .headers(headers -> headers.setBearerAuth("advertiser-token"))
                .exchange()
                .expectStatus().is2xxSuccessful();
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
        subscription.setCampaignId(campaignId);
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
}
