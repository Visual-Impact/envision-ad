package com.envisionad.webservice.admin.presentationlayer;

import com.envisionad.webservice.admin.presentationlayer.models.UpdateBusinessTypeRequestModel;
import com.envisionad.webservice.business.dataaccesslayer.Business;
import com.envisionad.webservice.business.dataaccesslayer.BusinessIdentifier;
import com.envisionad.webservice.business.dataaccesslayer.BusinessRepository;
import com.envisionad.webservice.business.dataaccesslayer.Roles;
import com.envisionad.webservice.config.BaseIntegrationTest;
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
}
