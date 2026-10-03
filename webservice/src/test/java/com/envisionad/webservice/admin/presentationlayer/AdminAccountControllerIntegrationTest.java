package com.envisionad.webservice.admin.presentationlayer;

import com.envisionad.webservice.admin.presentationlayer.models.BusinessTypeChangeImpactResponseModel;
import com.envisionad.webservice.admin.presentationlayer.models.UpdateBusinessTypeRequestModel;
import com.envisionad.webservice.business.dataaccesslayer.Business;
import com.envisionad.webservice.business.dataaccesslayer.BusinessIdentifier;
import com.envisionad.webservice.business.dataaccesslayer.BusinessRepository;
import com.envisionad.webservice.business.dataaccesslayer.Roles;
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
import com.envisionad.webservice.venue.dataaccesslayer.Venue;
import com.envisionad.webservice.venue.dataaccesslayer.VenueRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * P4's admin business-type endpoint against a real database. The rest of this controller
 * reaches Auth0 on every call and stays unit-tested (see AdminAccountServiceImplUnitTest);
 * this endpoint is purely local, so it can be exercised end to end.
 */
class AdminAccountControllerIntegrationTest extends BaseIntegrationTest {

    private static final String BUSINESS_ID = "p4-business-type-test-business";
    private static final String URI = "/api/v1/admin/accounts/" + BUSINESS_ID + "/business-type";
    private static final String TOKEN = "admin-token";

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private MediaRepository mediaRepository;

    @Autowired
    private MediaLocationRepository mediaLocationRepository;

    @Autowired
    private BundleSubscriptionRepository subscriptionRepository;

    @Autowired
    private BundleSubscriptionItemRepository subscriptionItemRepository;

    private final List<Media> createdMedia = new ArrayList<>();
    private final List<BundleSubscription> createdSubscriptions = new ArrayList<>();
    private final List<BundleSubscriptionItem> createdItems = new ArrayList<>();

    private String gymVenueId;

    @BeforeEach
    void setUp() {
        Venue gym = new Venue();
        gym.setVenueId(UUID.randomUUID().toString());
        gym.setNameEn("Gym");
        gym.setNameFr("Gym");
        gym.setColorCode("#00BFFF");
        gymVenueId = venueRepository.save(gym).getVenueId();

        Roles advertiser = new Roles();
        advertiser.setAdvertiser(true);
        Business business = new Business();
        business.setBusinessId(new BusinessIdentifier(BUSINESS_ID));
        business.setName("P4 business-type test business");
        business.setOwnerId("auth0|p4owner");
        business.setRoles(advertiser);
        businessRepository.save(business);

        authWith(List.of("manage:accounts"));
    }

    @AfterEach
    void tearDown() {
        subscriptionItemRepository.deleteAll(createdItems);
        subscriptionRepository.deleteAll(createdSubscriptions);
        createdMedia.forEach(media -> {
            mediaRepository.delete(media);
            mediaLocationRepository.delete(media.getMediaLocation());
        });
        Business business = businessRepository.findByBusinessId_BusinessId(BUSINESS_ID);
        if (business != null) {
            businessRepository.delete(business);
        }
        venueRepository.findByVenueId(gymVenueId).ifPresent(venueRepository::delete);
    }

    private void authWith(List<String> permissions) {
        Jwt jwt = Jwt.withTokenValue(TOKEN)
                .header("alg", "none")
                .claim("sub", "auth0|admin")
                .claim("permissions", permissions)
                .build();
        when(jwtDecoder.decode(anyString())).thenReturn(jwt);
    }

    private WebTestClient.ResponseSpec patch(String businessTypeVenueId) {
        UpdateBusinessTypeRequestModel body = new UpdateBusinessTypeRequestModel();
        body.setBusinessTypeVenueId(businessTypeVenueId);
        return webTestClient.patch()
                .uri(URI)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(TOKEN))
                .body(BodyInserters.fromValue(body))
                .exchange();
    }

    private String storedBusinessType() {
        return businessRepository.findByBusinessId_BusinessId(BUSINESS_ID).getBusinessTypeVenueId();
    }

    @Test
    void patch_withManageAccounts_setsTheBusinessType() {
        patch(gymVenueId)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.businessTypeVenueId").isEqualTo(gymVenueId);

        assertEquals(gymVenueId, storedBusinessType());
    }

    @Test
    void patch_withNull_clearsTheBusinessType() {
        Business business = businessRepository.findByBusinessId_BusinessId(BUSINESS_ID);
        business.setBusinessTypeVenueId(gymVenueId);
        businessRepository.save(business);

        patch(null).expectStatus().isOk();

        assertNull(storedBusinessType());
    }

    @Test
    void patch_withAnUnknownVenue_isNotFound_andLeavesTheTypeAlone() {
        patch("no-such-venue").expectStatus().isNotFound();

        assertNull(storedBusinessType());
    }

    @Test
    void patch_forAnUnknownBusiness_isNotFound() {
        UpdateBusinessTypeRequestModel body = new UpdateBusinessTypeRequestModel();
        body.setBusinessTypeVenueId(gymVenueId);

        webTestClient.patch()
                .uri("/api/v1/admin/accounts/no-such-business/business-type")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(TOKEN))
                .body(BodyInserters.fromValue(body))
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void patch_withoutManageAccounts_isForbidden() {
        // update:business is what every business owner holds — it must not reach this endpoint.
        authWith(List.of("update:business"));

        patch(gymVenueId).expectStatus().isForbidden();

        assertNull(storedBusinessType());
    }

    // --- GET .../business-type/impact: the admin modal's warning before a type is set ---

    private Media givenScreen(String venueId) {
        MediaLocation location = new MediaLocation();
        location.setName("Impact test location");
        location.setCountry("Canada");
        location.setProvince("QC");
        location.setCity("Montreal");
        location.setStreet("1 Test St");
        location.setPostalCode("H1H 1H1");
        location.setLatitude(45.5);
        location.setLongitude(-73.5);
        location.setBusinessId(UUID.randomUUID());

        Media media = new Media();
        media.setMediaLocation(mediaLocationRepository.save(location));
        media.setTitle("Impact test screen");
        media.setMediaOwnerName("Owner");
        media.setTypeOfDisplay(TypeOfDisplay.DIGITAL);
        media.setStatus(Status.ACTIVE);
        media.setVenueId(venueId);
        media.setPrice(new BigDecimal("4.00"));
        media.setBusinessId(UUID.randomUUID());
        Media saved = mediaRepository.save(media);
        createdMedia.add(saved);
        return saved;
    }

    private void givenSubscription(String advertiserBusinessId, BundleSubscriptionStatus status, Media... screens) {
        BundleSubscription subscription = new BundleSubscription();
        subscription.setBundleId(UUID.randomUUID().toString());
        subscription.setAdvertiserBusinessId(advertiserBusinessId);
        subscription.setStripeCheckoutSessionId("cs_impact_" + UUID.randomUUID());
        subscription.setStatus(status);
        subscription.setMonthlyAmount(new BigDecimal("4.00").multiply(BigDecimal.valueOf(screens.length)));
        subscription.setScreenCount(screens.length);
        BundleSubscription saved = subscriptionRepository.save(subscription);
        createdSubscriptions.add(saved);
        for (Media screen : screens) {
            BundleSubscriptionItem item = new BundleSubscriptionItem();
            item.setSubscriptionId(saved.getSubscriptionId());
            item.setMediaId(screen.getId());
            item.setMediaOwnerBusinessId("impact-owner");
            item.setMonthlyAmount(new BigDecimal("4.00"));
            createdItems.add(subscriptionItemRepository.save(item));
        }
    }

    private WebTestClient.ResponseSpec impact(String businessId, String businessTypeVenueId) {
        return webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/admin/accounts/" + businessId + "/business-type/impact")
                        .queryParam("businessTypeVenueId", businessTypeVenueId)
                        .build())
                .headers(headers -> headers.setBearerAuth(TOKEN))
                .exchange();
    }

    @Test
    void impact_countsDistinctScreensOfThatVenueInTheAdvertisersLiveSubscriptionsOnly() {
        Media gymA = givenScreen(gymVenueId);
        Media gymB = givenScreen(gymVenueId);
        Media cafe = givenScreen("some-other-venue");
        Media gymInCanceled = givenScreen(gymVenueId);
        Media gymOfAnotherAdvertiser = givenScreen(gymVenueId);
        givenSubscription(BUSINESS_ID, BundleSubscriptionStatus.ACTIVE, gymA, cafe);
        // gymA again: one screen bought through two bundles counts once.
        givenSubscription(BUSINESS_ID, BundleSubscriptionStatus.PAST_DUE, gymA, gymB);
        givenSubscription(BUSINESS_ID, BundleSubscriptionStatus.CANCELED, gymInCanceled);
        givenSubscription("some-other-advertiser", BundleSubscriptionStatus.ACTIVE, gymOfAnotherAdvertiser);

        impact(BUSINESS_ID, gymVenueId)
                .expectStatus().isOk()
                .expectBody(BusinessTypeChangeImpactResponseModel.class)
                .value(body -> assertEquals(2, body.getLiveSubscriptionScreenCount()));
    }

    @Test
    void impact_ofClearingTheType_isZero() {
        givenSubscription(BUSINESS_ID, BundleSubscriptionStatus.ACTIVE, givenScreen(gymVenueId));

        impact(BUSINESS_ID, " ")
                .expectStatus().isOk()
                .expectBody(BusinessTypeChangeImpactResponseModel.class)
                .value(body -> assertEquals(0, body.getLiveSubscriptionScreenCount()));
    }

    @Test
    void impact_forAnUnknownBusiness_isNotFound() {
        impact("no-such-business", gymVenueId).expectStatus().isNotFound();
    }

    @Test
    void impact_withoutManageAccounts_isForbidden() {
        authWith(List.of("update:business"));

        impact(BUSINESS_ID, gymVenueId).expectStatus().isForbidden();
    }
}
