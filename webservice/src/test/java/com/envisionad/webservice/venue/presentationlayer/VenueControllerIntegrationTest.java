package com.envisionad.webservice.venue.presentationlayer;

import com.envisionad.webservice.advertisement.dataaccesslayer.*;
import com.envisionad.webservice.business.dataaccesslayer.BusinessIdentifier;
import com.envisionad.webservice.config.BaseIntegrationTest;
import com.envisionad.webservice.venue.dataaccesslayer.Venue;
import com.envisionad.webservice.venue.dataaccesslayer.VenueRepository;
import com.envisionad.webservice.venue.presentationlayer.models.VenueRequestModel;
import com.envisionad.webservice.venue.presentationlayer.models.VenueResponseModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class VenueControllerIntegrationTest extends BaseIntegrationTest {

    private static final String BASE_URI = "/api/v1/venues";

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private AdCampaignRepository adCampaignRepository;

    @Autowired
    private AdRepository adRepository;

    private Venue savedVenue;

    @BeforeEach
    void setUp() {
        // Campaigns first: ad_venue_tags FKs venue, and under ddl-auto: create that FK
        // has no cascade, so a leftover tagged ad would block venueRepository.deleteAll().
        adCampaignRepository.deleteAll();
        venueRepository.deleteAll();

        Jwt adminJwt = Jwt.withTokenValue("mock-token")
                .header("alg", "none")
                .claim("sub", "auth0|admin123")
                .claim("permissions", List.of("manage:venues"))
                .build();
        when(jwtDecoder.decode(anyString())).thenReturn(adminJwt);

        Venue venue = new Venue();
        venue.setVenueId("test-venue-id-1");
        venue.setNameEn("Barbershop");
        venue.setNameFr("Salon de coiffure");
        venue.setColorCode("#FF5733");
        savedVenue = venueRepository.save(venue);
    }

    @Test
    void getAllVenues_returnsOkWithVenues() {
        webTestClient.get()
                .uri(BASE_URI)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(VenueResponseModel.class)
                .hasSize(1)
                .value(venues -> {
                    assertEquals("Barbershop", venues.get(0).getNameEn());
                    assertEquals("#FF5733", venues.get(0).getColorCode());
                });
    }

    @Test
    void getAllVenues_withFrLocale_returnsOrderedByFrenchName() {
        Venue venue2 = new Venue();
        venue2.setVenueId("test-venue-id-2");
        venue2.setNameEn("Gym");
        venue2.setNameFr("Gymnase");
        venue2.setColorCode("#3366FF");
        venueRepository.save(venue2);

        webTestClient.get()
                .uri(BASE_URI + "?locale=fr")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(VenueResponseModel.class)
                .hasSize(2)
                .value(venues -> {
                    assertEquals("Gymnase", venues.get(0).getNameFr());
                    assertEquals("Salon de coiffure", venues.get(1).getNameFr());
                });
    }

    @Test
    void getVenueByVenueId_existingVenue_returnsOk() {
        webTestClient.get()
                .uri(BASE_URI + "/{venueId}", savedVenue.getVenueId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(VenueResponseModel.class)
                .value(venue -> {
                    assertEquals("Barbershop", venue.getNameEn());
                    assertEquals(savedVenue.getVenueId(), venue.getVenueId());
                });
    }

    @Test
    void getVenueByVenueId_nonExistingVenue_returnsNotFound() {
        webTestClient.get()
                .uri(BASE_URI + "/{venueId}", "non-existent-id")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void createVenue_withAdminAuth_returnsCreated() {
        VenueRequestModel request = new VenueRequestModel();
        request.setNameEn("College");
        request.setNameFr("Collège");
        request.setColorCode("#00FF00");

        webTestClient.post()
                .uri(BASE_URI)
                .header("Authorization", "Bearer mock-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(VenueResponseModel.class)
                .value(venue -> {
                    assertNotNull(venue.getVenueId());
                    assertEquals("College", venue.getNameEn());
                    assertEquals("Collège", venue.getNameFr());
                    assertEquals("#00FF00", venue.getColorCode());
                    assertEquals(0, venue.getMediaCount());
                });
    }

    @Test
    void createVenue_withoutAuth_returnsUnauthorized() {
        when(jwtDecoder.decode(anyString())).thenReturn(
                Jwt.withTokenValue("mock-token")
                        .header("alg", "none")
                        .claim("sub", "auth0|user123")
                        .claim("permissions", List.of())
                        .build()
        );

        VenueRequestModel request = new VenueRequestModel();
        request.setNameEn("College");
        request.setNameFr("Collège");
        request.setColorCode("#00FF00");

        webTestClient.post()
                .uri(BASE_URI)
                .header("Authorization", "Bearer mock-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void updateVenue_withAdminAuth_returnsOk() {
        VenueRequestModel request = new VenueRequestModel();
        request.setNameEn("Barber Shop Updated");
        request.setNameFr("Salon de coiffure mis à jour");
        request.setColorCode("#AABBCC");

        webTestClient.put()
                .uri(BASE_URI + "/{venueId}", savedVenue.getVenueId())
                .header("Authorization", "Bearer mock-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(VenueResponseModel.class)
                .value(venue -> {
                    assertEquals("Barber Shop Updated", venue.getNameEn());
                    assertEquals("#AABBCC", venue.getColorCode());
                });
    }

    @Test
    void deleteVenue_withAdminAuth_returnsNoContent() {
        webTestClient.delete()
                .uri(BASE_URI + "/{venueId}", savedVenue.getVenueId())
                .header("Authorization", "Bearer mock-token")
                .exchange()
                .expectStatus().isNoContent();

        assertFalse(venueRepository.findByVenueId(savedVenue.getVenueId()).isPresent());
    }

    @Test
    void deleteVenue_nonExistingVenue_returnsNotFound() {
        webTestClient.delete()
                .uri(BASE_URI + "/{venueId}", "non-existent-id")
                .header("Authorization", "Bearer mock-token")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void getMediaCount_withAdminAuth_returnsZero() {
        webTestClient.get()
                .uri(BASE_URI + "/{venueId}/media-count", savedVenue.getVenueId())
                .header("Authorization", "Bearer mock-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Long.class)
                .value(count -> assertEquals(0L, count));
    }

    // ---------------- P7: ad venue tags ----------------

    @Test
    void getAdCount_withAdminAuth_noTags_returnsZero() {
        expectAdCount(savedVenue.getVenueId(), 0);
    }

    @Test
    void getAdCount_withAdminAuth_returnsCount() {
        persistCampaignWithTaggedAd(savedVenue);
        persistCampaignWithTaggedAd(savedVenue);

        expectAdCount(savedVenue.getVenueId(), 2);
    }

    @Test
    void getAdCount_nonExistingVenue_returnsZero() {
        // Mirrors media-count, which also 200s with 0 rather than 404ing on an unknown
        // venue. Asserted so the asymmetry with the rest of the controller is deliberate.
        expectAdCount("no-such-venue", 0);
    }

    @Test
    void getAdCount_withoutManageVenuesPermission_returns403() {
        Jwt noPermsJwt = Jwt.withTokenValue("no-perms-token")
                .header("alg", "none")
                .claim("sub", "auth0|nobody")
                .claim("permissions", List.of())
                .build();
        when(jwtDecoder.decode("no-perms-token")).thenReturn(noPermsJwt);

        webTestClient.get()
                .uri(BASE_URI + "/{venueId}/ad-count", savedVenue.getVenueId())
                .header("Authorization", "Bearer no-perms-token")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void deleteVenue_taggedOnAnAd_untagsTheAdAndReturns204() {
        // FR-6.2. Only reachable because deleteVenue() untags in application code — the
        // migration's ON DELETE CASCADE does not exist in the entity-generated test schema.
        persistCampaignWithTaggedAd(savedVenue);

        webTestClient.delete()
                .uri(BASE_URI + "/{venueId}", savedVenue.getVenueId())
                .header("Authorization", "Bearer mock-token")
                .exchange()
                .expectStatus().isNoContent();

        assertTrue(venueRepository.findByVenueId(savedVenue.getVenueId()).isEmpty());

        // Asserted via queries rather than ad.getVenues(): open-in-view keeps a session
        // open for web requests, but not for a test method body.
        assertEquals(1, adRepository.count(), "the ad itself must survive");
        assertTrue(adRepository.findByVenues_VenueId(savedVenue.getVenueId()).isEmpty(),
                "the tag must be gone");
    }

    private void expectAdCount(String venueId, long expected) {
        webTestClient.get()
                .uri(BASE_URI + "/{venueId}/ad-count", venueId)
                .header("Authorization", "Bearer mock-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.adCount").isEqualTo(expected);
    }

    private String persistCampaignWithTaggedAd(Venue venue) {
        AdCampaign campaign = new AdCampaign();
        campaign.setName("Tagged Campaign");
        campaign.setCampaignId(new AdCampaignIdentifier());
        campaign.setBusinessId(new BusinessIdentifier(UUID.randomUUID().toString()));

        Ad ad = new Ad();
        ad.setAdIdentifier(new AdIdentifier());
        ad.setName("Tagged Ad");
        ad.setAdUrl("https://cdn.envisionad.com/tagged.jpg");
        ad.setAdType(AdType.IMAGE);
        ad.setCampaign(campaign);
        ad.setVenues(List.of(venue));

        campaign.setAds(new ArrayList<>(List.of(ad)));

        return adCampaignRepository.save(campaign).getCampaignId().getCampaignId();
    }
}
