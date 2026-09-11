package com.envisionad.webservice.activecampaign.presentationlayer;

import com.envisionad.webservice.activecampaign.dataaccesslayer.CampaignSwapEvent;
import com.envisionad.webservice.activecampaign.dataaccesslayer.CampaignSwapEventRepository;
import com.envisionad.webservice.activecampaign.dataaccesslayer.CampaignSwapEventType;
import com.envisionad.webservice.advertisement.dataaccesslayer.*;
import com.envisionad.webservice.business.dataaccesslayer.*;
import com.envisionad.webservice.config.BaseIntegrationTest;
import com.envisionad.webservice.venue.dataaccesslayer.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * The five active-campaign endpoints against a real Postgres, exercising the wiring the unit
 * tests deliberately mock out: authority checks, the 204-vs-200 distinction, and that the 429
 * body really does carry the countdown the frontend reads.
 */
public class ActiveCampaignIntegrationTest extends BaseIntegrationTest {

    private static final String BASE = "/api/v1/businesses/{businessId}/active-campaign";
    private static final String BUSINESS_ID = "c1eebc99-9c0b-4ef8-bb6d-6bb9bd380b33";
    private static final String TEST_USER_ID = "auth0|696a88eb347945897ef17093";

    @Autowired private BusinessRepository businessRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private AdCampaignRepository adCampaignRepository;
    @Autowired private CampaignSwapEventRepository campaignSwapEventRepository;
    @Autowired private VenueRepository venueRepository;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private BusinessIdentifier businessId;

    @BeforeEach
    void setUp() {
        campaignSwapEventRepository.deleteAll();
        // The business row points at a campaign, so clear the pointer before the campaigns go.
        businessRepository.findAll().forEach(business -> {
            business.setActiveCampaignId(null);
            businessRepository.save(business);
        });
        adCampaignRepository.deleteAll();
        venueRepository.deleteAll();
        employeeRepository.deleteAll();
        businessRepository.deleteAll();

        Jwt advertiser = Jwt.withTokenValue("advertiser-token")
                .header("alg", "none")
                .claim("sub", TEST_USER_ID)
                .claim("scope", "read write")
                .claim("permissions", List.of("readAll:campaign", "read:campaign", "create:campaign",
                        "update:campaign", "delete:campaign"))
                .build();
        Jwt outsider = Jwt.withTokenValue("outsider-token")
                .header("alg", "none")
                .claim("sub", "auth0|696b10a00bba0a28c21d3829")
                .claim("scope", "read write")
                .claim("permissions", List.of("readAll:campaign", "update:campaign"))
                .build();
        when(jwtDecoder.decode("advertiser-token")).thenReturn(advertiser);
        when(jwtDecoder.decode("outsider-token")).thenReturn(outsider);

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

        Employee employee = new Employee();
        employee.setEmployeeId(new EmployeeIdentifier());
        employee.setBusinessId(businessId);
        employee.setUserId(TEST_USER_ID);
        employeeRepository.save(employee);
    }

    @Test
    void getActiveCampaign_whenNoneIsSet_is204NotAnEmptyBody() {
        webTestClient.get().uri(BASE, BUSINESS_ID)
                .headers(headers -> headers.setBearerAuth("advertiser-token"))
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void selectThenGet_returnsTheCampaignWithRealSubscriptionCounts() {
        AdCampaign campaign = givenCampaignWithAds("Summer Sale", 2);

        webTestClient.post().uri(BASE + "/select", BUSINESS_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth("advertiser-token"))
                .bodyValue(Map.of("campaignId", campaign.getCampaignId().getCampaignId()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("Summer Sale")
                .jsonPath("$.ads.length()").isEqualTo(2)
                // Real numbers, not the placeholder nulls brief 06 allowed for before P1 shipped.
                .jsonPath("$.subscribedBundleCount").isEqualTo(0)
                .jsonPath("$.subscribedScreenCount").isEqualTo(0)
                .jsonPath("$.swapAvailableAt").doesNotExist();

        assertEquals(campaign.getCampaignId().getCampaignId(),
                businessRepository.findByBusinessId_BusinessId(BUSINESS_ID).getActiveCampaignId());

        List<CampaignSwapEvent> events = campaignSwapEventRepository.findAll();
        assertEquals(1, events.size());
        assertEquals(CampaignSwapEventType.INITIAL_SELECTION, events.get(0).getEventType());
        assertEquals(0, events.get(0).getRecipientsNotified(), "an initial selection emails nobody");
    }

    @Test
    void select_whenOneIsAlreadySet_is409() {
        AdCampaign first = givenCampaignWithAds("Summer Sale", 1);
        AdCampaign second = givenCampaignWithAds("Winter Sale", 1);
        givenActiveCampaign(first);

        webTestClient.post().uri(BASE + "/select", BUSINESS_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth("advertiser-token"))
                .bodyValue(Map.of("campaignId", second.getCampaignId().getCampaignId()))
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    /**
     * Ruled 2026-09-09: 409, not brief 06's 422. The exception predates P6 and was already
     * registered as a conflict, and "this campaign has nothing to show" is a state problem
     * rather than a malformed request.
     */
    @Test
    void select_aCampaignWithNoCreatives_is409() {
        AdCampaign empty = givenCampaignWithAds("Empty", 0);

        webTestClient.post().uri(BASE + "/select", BUSINESS_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth("advertiser-token"))
                .bodyValue(Map.of("campaignId", empty.getCampaignId().getCampaignId()))
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void swap_movesThePointerAndWritesASwapEvent() {
        AdCampaign first = givenCampaignWithAds("Summer Sale", 1);
        AdCampaign second = givenCampaignWithAds("Winter Sale", 1);
        givenActiveCampaign(first);

        webTestClient.put().uri(BASE, BUSINESS_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth("advertiser-token"))
                .bodyValue(Map.of("campaignId", second.getCampaignId().getCampaignId()))
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.name").isEqualTo("Winter Sale");

        assertEquals(second.getCampaignId().getCampaignId(),
                businessRepository.findByBusinessId_BusinessId(BUSINESS_ID).getActiveCampaignId());
        CampaignSwapEvent event = campaignSwapEventRepository.findAll().get(0);
        assertEquals(CampaignSwapEventType.SWAP, event.getEventType());
        assertEquals(first.getCampaignId().getCampaignId(), event.getFromCampaignId());
        assertEquals(TEST_USER_ID, event.getTriggeredByUserId());
    }

    /** The countdown the UI renders has to actually be in the body, not just implied by the 429. */
    @Test
    void swap_insideTheCooldown_is429WithRetryAfterSecondsInTheBody() {
        AdCampaign first = givenCampaignWithAds("Summer Sale", 1);
        AdCampaign second = givenCampaignWithAds("Winter Sale", 1);
        givenActiveCampaign(first);
        givenRecentEvent(CampaignSwapEventType.SWAP, LocalDateTime.now().minusMinutes(2));

        webTestClient.put().uri(BASE, BUSINESS_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth("advertiser-token"))
                .bodyValue(Map.of("campaignId", second.getCampaignId().getCampaignId()))
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectBody()
                .jsonPath("$.code").isEqualTo("SWAP_DEBOUNCED")
                .jsonPath("$.retryAfterSeconds").value(seconds ->
                        assertTrue((int) seconds > 0 && (int) seconds <= 8 * 60,
                                "expected roughly eight minutes left, got " + seconds));

        assertEquals(first.getCampaignId().getCampaignId(),
                businessRepository.findByBusinessId_BusinessId(BUSINESS_ID).getActiveCampaignId(),
                "a refused swap must not move the pointer");
    }

    /** Every other error body keeps its existing shape — the new field is omitted, not null. */
    @Test
    void anOrdinaryErrorBody_carriesNoRetryAfterSecondsField() {
        webTestClient.post().uri(BASE + "/select", BUSINESS_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth("advertiser-token"))
                .bodyValue(Map.of("campaignId", "does-not-exist"))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.retryAfterSeconds").doesNotExist();
    }

    @Test
    void swap_bySomeoneWhoIsNotAnEmployeeOfTheBusiness_isForbidden() {
        AdCampaign first = givenCampaignWithAds("Summer Sale", 1);
        AdCampaign second = givenCampaignWithAds("Winter Sale", 1);
        givenActiveCampaign(first);

        webTestClient.put().uri(BASE, BUSINESS_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth("outsider-token"))
                .bodyValue(Map.of("campaignId", second.getCampaignId().getCampaignId()))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void notify_withNothingOnScreen_is404() {
        webTestClient.post().uri(BASE + "/notify", BUSINESS_ID)
                .headers(headers -> headers.setBearerAuth("advertiser-token"))
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void notify_withNoAffectedOwners_stillSucceedsAndRecordsZeroRecipients() {
        givenActiveCampaign(givenCampaignWithAds("Summer Sale", 1));

        webTestClient.post().uri(BASE + "/notify", BUSINESS_ID)
                .headers(headers -> headers.setBearerAuth("advertiser-token"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.notifiedCount").isEqualTo(0)
                .jsonPath("$.failedCount").isEqualTo(0);

        assertEquals(CampaignSwapEventType.MANUAL_NOTIFY,
                campaignSwapEventRepository.findAll().get(0).getEventType());
    }

    @Test
    void eligibleForSwap_omitsTheActiveCampaignAndAnyWithoutCreatives() {
        AdCampaign active = givenCampaignWithAds("Summer Sale", 1);
        AdCampaign alternative = givenCampaignWithAds("Winter Sale", 1);
        givenCampaignWithAds("Empty Draft", 0);
        givenActiveCampaign(active);

        webTestClient.get().uri("/api/v1/businesses/{businessId}/campaigns/eligible-for-swap", BUSINESS_ID)
                .headers(headers -> headers.setBearerAuth("advertiser-token"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].name").isEqualTo("Winter Sale")
                .jsonPath("$[0].campaignId").isEqualTo(alternative.getCampaignId().getCampaignId());
    }

    // ---------- helpers ----------

    private AdCampaign givenCampaignWithAds(String name, int adCount) {
        AdCampaign campaign = new AdCampaign();
        campaign.setCampaignId(new AdCampaignIdentifier());
        campaign.setBusinessId(businessId);
        campaign.setName(name);
        for (int i = 0; i < adCount; i++) {
            Ad ad = new Ad();
            ad.setAdIdentifier(new AdIdentifier());
            ad.setName(name + " creative " + i);
            ad.setAdUrl("https://cdn.example.com/" + name.replace(' ', '-') + i + ".jpg");
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

    /**
     * Writes an event that happened in the past.
     *
     * <p>The timestamp has to be forced in afterwards with SQL: {@code triggeredAt} is
     * {@code @CreationTimestamp} and {@code updatable = false}, so Hibernate overwrites whatever
     * the entity carries with the moment of the insert and silently ignores a backdated value.
     * That is correct for production — a real event always happens now — but it means no test
     * can age one through the repository, and a test that tries will quietly assert against a
     * fresh timestamp instead of failing.
     */
    private void givenRecentEvent(CampaignSwapEventType type, LocalDateTime triggeredAt) {
        CampaignSwapEvent event = new CampaignSwapEvent();
        event.setBusinessId(BUSINESS_ID);
        event.setToCampaignId(businessRepository.findByBusinessId_BusinessId(BUSINESS_ID).getActiveCampaignId());
        event.setEventType(type);
        CampaignSwapEvent saved = campaignSwapEventRepository.save(event);
        jdbcTemplate.update("UPDATE campaign_swap_events SET triggered_at = ? WHERE id = ?",
                java.sql.Timestamp.valueOf(triggeredAt), saved.getId());
    }
}
