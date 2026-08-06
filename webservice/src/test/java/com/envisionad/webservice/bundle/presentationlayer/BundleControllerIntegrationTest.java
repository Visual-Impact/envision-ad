package com.envisionad.webservice.bundle.presentationlayer;

import com.envisionad.webservice.bundle.dataaccesslayer.*;
import com.envisionad.webservice.bundle.presentationlayer.models.BundleCandidateMediaResponseModel;
import com.envisionad.webservice.bundle.presentationlayer.models.BundlePriceQuoteResponseModel;
import com.envisionad.webservice.bundle.presentationlayer.models.BundleRequestModel;
import com.envisionad.webservice.bundle.presentationlayer.models.BundleResponseModel;
import com.envisionad.webservice.business.dataaccesslayer.BusinessIdentifier;
import com.envisionad.webservice.business.dataaccesslayer.Employee;
import com.envisionad.webservice.business.dataaccesslayer.EmployeeIdentifier;
import com.envisionad.webservice.business.dataaccesslayer.EmployeeRepository;
import com.envisionad.webservice.config.BaseIntegrationTest;
import com.envisionad.webservice.media.DataAccessLayer.*;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscription;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionRepository;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionStatus;
import com.envisionad.webservice.venue.dataaccesslayer.Venue;
import com.envisionad.webservice.venue.dataaccesslayer.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * End-to-end coverage of the bundle module, including rule matching against real
 * media rows — the part a mocked {@code Specification} could never prove.
 */
class BundleControllerIntegrationTest extends BaseIntegrationTest {

    private static final String BASE_URI = "/api/v1/bundles";
    private static final String ADMIN_TOKEN = "admin-token";

    @Autowired
    private BundleRepository bundleRepository;

    @Autowired
    private BundleExcludedMediaRepository excludedMediaRepository;

    @Autowired
    private BundleSubscriptionRepository subscriptionRepository;

    @Autowired
    private MediaRepository mediaRepository;

    @Autowired
    private MediaLocationRepository mediaLocationRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private VenueRepository venueRepository;

    private Media montrealMedia;
    private Media lavalMedia;

    @BeforeEach
    void setUp() {
        subscriptionRepository.deleteAll();
        excludedMediaRepository.deleteAll();
        bundleRepository.deleteAll();
        mediaRepository.deleteAll();
        mediaLocationRepository.deleteAll();
        venueRepository.deleteAll();
        employeeRepository.deleteAll();

        Jwt adminJwt = Jwt.withTokenValue(ADMIN_TOKEN)
                .header("alg", "none")
                .claim("sub", "auth0|admin123")
                .claim("permissions", List.of("manage:bundles"))
                .build();
        when(jwtDecoder.decode(anyString())).thenReturn(adminJwt);

        montrealMedia = givenMedia("Downtown board", "Montreal", "Montérégie", "venue-gym", Status.ACTIVE, "4.00");
        lavalMedia = givenMedia("Laval board", "Laval", "Laurentides", "venue-cafe", Status.ACTIVE, "6.50");
        givenMedia("Pending board", "Montreal", "Montérégie", "venue-gym", Status.PENDING, "99.00");
    }

    private Media givenMedia(String title, String city, String region, String venueId,
            Status status, String price) {
        MediaLocation location = new MediaLocation();
        location.setName(title + " location");
        location.setCountry("Canada");
        location.setProvince("QC");
        location.setCity(city);
        location.setRegion(region);
        location.setStreet("123 Main St");
        location.setPostalCode("H1H 1H1");
        location.setLatitude(45.5017);
        location.setLongitude(-73.5673);
        location.setBusinessId(UUID.randomUUID());
        MediaLocation savedLocation = mediaLocationRepository.save(location);

        Media media = new Media();
        media.setMediaLocation(savedLocation);
        media.setTitle(title);
        media.setMediaOwnerName("Owner");
        media.setTypeOfDisplay(TypeOfDisplay.DIGITAL);
        media.setStatus(status);
        media.setVenueId(venueId);
        media.setPrice(new BigDecimal(price));
        media.setBusinessId(UUID.randomUUID());
        return mediaRepository.save(media);
    }

    private Bundle givenBundle(BundleRuleType ruleType, String ruleValue, boolean active) {
        Bundle bundle = new Bundle();
        bundle.setNameEn("Bundle " + ruleType);
        bundle.setNameFr("Forfait " + ruleType);
        bundle.setBadgeColor("#FF5733");
        bundle.setRuleType(ruleType);
        bundle.setRuleValue(ruleValue);
        bundle.setActive(active);
        return bundleRepository.save(bundle);
    }

    private Venue givenVenue(String venueId, String nameEn, String nameFr) {
        Venue venue = new Venue();
        venue.setVenueId(venueId);
        venue.setNameEn(nameEn);
        venue.setNameFr(nameFr);
        venue.setColorCode("#00BFFF");
        return venueRepository.save(venue);
    }

    private BundleRequestModel requestModel(BundleRuleType ruleType, String ruleValue) {
        BundleRequestModel request = new BundleRequestModel();
        request.setNameEn("Full Network");
        request.setNameFr("Réseau complet");
        request.setDescriptionEn("Every active screen.");
        request.setDescriptionFr("Tous les écrans actifs.");
        request.setIdealForEn("Local retailers");
        request.setIdealForFr("Détaillants locaux");
        request.setBadgeColor("#3366FF");
        request.setRuleType(ruleType);
        request.setRuleValue(ruleValue);
        return request;
    }

    // ---------- rule matching ----------

    @Test
    void fullNetworkBundle_countsEveryActiveMediaAndSumsTheirPrices() {
        Bundle bundle = givenBundle(BundleRuleType.FULL_NETWORK, null, true);

        webTestClient.get().uri(BASE_URI + "/" + bundle.getBundleId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(BundleResponseModel.class)
                .value(body -> {
                    assertEquals(2, body.getScreenCount(), "the PENDING board must not count");
                    assertEquals(0, new BigDecimal("10.50").compareTo(body.getBasePrice()));
                });
    }

    @Test
    void cityBundle_matchesCaseInsensitivelyAndIgnoringWhitespace() {
        Bundle bundle = givenBundle(BundleRuleType.CITY, "  mOnTrEaL  ", true);

        webTestClient.get().uri(BASE_URI + "/" + bundle.getBundleId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(BundleResponseModel.class)
                .value(body -> {
                    assertEquals(1, body.getScreenCount());
                    assertEquals(0, new BigDecimal("4.00").compareTo(body.getBasePrice()));
                });
    }

    @Test
    void regionBundle_matchesOnMediaLocationRegion() {
        Bundle bundle = givenBundle(BundleRuleType.REGION, "laurentides", true);

        webTestClient.get().uri(BASE_URI + "/" + bundle.getBundleId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(BundleResponseModel.class)
                .value(body -> {
                    assertEquals(1, body.getScreenCount());
                    assertEquals(0, new BigDecimal("6.50").compareTo(body.getBasePrice()));
                });
    }

    @Test
    void venueBundle_matchesOnMediaVenueId() {
        Bundle bundle = givenBundle(BundleRuleType.VENUE, "venue-cafe", true);

        webTestClient.get().uri(BASE_URI + "/" + bundle.getBundleId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(BundleResponseModel.class)
                .value(body -> assertEquals(1, body.getScreenCount()));
    }

    // ---------- rule value labels ----------

    @Test
    void venueBundle_labelsTheRuleWithTheVenueNameInBothLanguages() {
        givenVenue("venue-cafe", "Coffee shop", "Café");
        Bundle bundle = givenBundle(BundleRuleType.VENUE, "venue-cafe", true);

        webTestClient.get().uri(BASE_URI + "/" + bundle.getBundleId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(BundleResponseModel.class)
                .value(body -> {
                    assertEquals("venue-cafe", body.getRuleValue(), "the stored rule is still the id");
                    assertEquals("Coffee shop", body.getRuleValueLabelEn());
                    assertEquals("Café", body.getRuleValueLabelFr());
                });
    }

    @Test
    void venueBundle_pointingAtAMissingVenue_fallsBackToTheRawId() {
        Bundle bundle = givenBundle(BundleRuleType.VENUE, "venue-deleted", true);

        webTestClient.get().uri(BASE_URI + "/" + bundle.getBundleId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(BundleResponseModel.class)
                .value(body -> {
                    assertEquals("venue-deleted", body.getRuleValueLabelEn());
                    assertEquals("venue-deleted", body.getRuleValueLabelFr());
                });
    }

    @Test
    void cityBundle_labelsBothLanguagesWithTheRawCityString() {
        // Free text with nothing to translate — a null French label would blank the
        // rule cell for a French admin, which is what the label field exists to avoid.
        Bundle bundle = givenBundle(BundleRuleType.CITY, "Montreal", true);

        webTestClient.get().uri(BASE_URI + "/" + bundle.getBundleId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(BundleResponseModel.class)
                .value(body -> {
                    assertEquals("Montreal", body.getRuleValueLabelEn());
                    assertEquals("Montreal", body.getRuleValueLabelFr());
                });
    }

    @Test
    void regionBundle_labelsBothLanguagesWithTheRawRegionString() {
        Bundle bundle = givenBundle(BundleRuleType.REGION, "Laurentides", true);

        webTestClient.get().uri(BASE_URI + "/" + bundle.getBundleId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(BundleResponseModel.class)
                .value(body -> {
                    assertEquals("Laurentides", body.getRuleValueLabelEn());
                    assertEquals("Laurentides", body.getRuleValueLabelFr());
                });
    }

    @Test
    void fullNetworkBundle_hasNoRuleValueLabel() {
        Bundle bundle = givenBundle(BundleRuleType.FULL_NETWORK, null, true);

        webTestClient.get().uri(BASE_URI + "/" + bundle.getBundleId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(BundleResponseModel.class)
                .value(body -> {
                    assertNull(body.getRuleValueLabelEn());
                    assertNull(body.getRuleValueLabelFr());
                });
    }

    @Test
    void bundleMatchingNothing_quotesZero() {
        Bundle bundle = givenBundle(BundleRuleType.CITY, "Vancouver", true);

        webTestClient.get().uri(BASE_URI + "/" + bundle.getBundleId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(BundleResponseModel.class)
                .value(body -> {
                    assertEquals(0, body.getScreenCount());
                    assertEquals(0, BigDecimal.ZERO.compareTo(body.getBasePrice()));
                });
    }

    // ---------- listing ----------

    @Test
    void getAllBundles_isPublicAndDefaultsToActiveOnly() {
        givenBundle(BundleRuleType.CITY, "Montreal", true);
        givenBundle(BundleRuleType.CITY, "Laval", false);

        webTestClient.get().uri(BASE_URI)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(BundleResponseModel.class)
                .hasSize(1);
    }

    @Test
    void getAllBundles_activeFalseIncludesDeactivated() {
        givenBundle(BundleRuleType.CITY, "Montreal", true);
        givenBundle(BundleRuleType.CITY, "Laval", false);

        webTestClient.get().uri(BASE_URI + "?active=false")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(BundleResponseModel.class)
                .hasSize(2);
    }

    @Test
    void getAllBundles_filtersByRuleType() {
        givenBundle(BundleRuleType.CITY, "Montreal", true);
        givenBundle(BundleRuleType.FULL_NETWORK, null, true);

        webTestClient.get().uri(BASE_URI + "?ruleType=FULL_NETWORK")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(BundleResponseModel.class)
                .hasSize(1)
                .value(bundles -> assertEquals(BundleRuleType.FULL_NETWORK, bundles.get(0).getRuleType()));
    }

    @Test
    void getBundleByBundleId_unknownIdIs404() {
        webTestClient.get().uri(BASE_URI + "/no-such-bundle")
                .exchange()
                .expectStatus().isNotFound();
    }

    // ---------- admin CRUD ----------

    @Test
    void createBundle_asAdmin_returnsCreated() {
        webTestClient.post().uri(BASE_URI)
                .header("Authorization", "Bearer " + ADMIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestModel(BundleRuleType.CITY, "Montreal"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(BundleResponseModel.class)
                .value(body -> {
                    assertNotNull(body.getBundleId());
                    assertEquals("Full Network", body.getNameEn());
                    assertTrue(body.isActive());
                    assertEquals(1, body.getScreenCount());
                });
    }

    @Test
    void createBundle_fullNetworkIgnoresASuppliedRuleValue() {
        // The DB CHECK would otherwise reject it; the controller normalises first.
        webTestClient.post().uri(BASE_URI)
                .header("Authorization", "Bearer " + ADMIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestModel(BundleRuleType.FULL_NETWORK, "Montreal"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(BundleResponseModel.class)
                .value(body -> assertNull(body.getRuleValue()));
    }

    @Test
    void createBundle_withoutPermission_isRejected() {
        Jwt plainJwt = Jwt.withTokenValue("plain-token")
                .header("alg", "none")
                .claim("sub", "auth0|user123")
                .claim("permissions", List.of("read:media"))
                .build();
        when(jwtDecoder.decode(anyString())).thenReturn(plainJwt);

        webTestClient.post().uri(BASE_URI)
                .header("Authorization", "Bearer plain-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestModel(BundleRuleType.CITY, "Montreal"))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void createBundle_anonymously_isRejected() {
        webTestClient.post().uri(BASE_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestModel(BundleRuleType.CITY, "Montreal"))
                .exchange()
                .expectStatus().value(status -> assertTrue(status == 401 || status == 403,
                        "anonymous writes must not succeed, got " + status));
    }

    @Test
    void updateBundle_persistsTheChanges() {
        Bundle bundle = givenBundle(BundleRuleType.CITY, "Montreal", true);
        BundleRequestModel request = requestModel(BundleRuleType.REGION, "Laurentides");
        request.setActive(false);

        webTestClient.put().uri(BASE_URI + "/" + bundle.getBundleId())
                .header("Authorization", "Bearer " + ADMIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(BundleResponseModel.class)
                .value(body -> {
                    assertEquals(BundleRuleType.REGION, body.getRuleType());
                    assertEquals("Laurentides", body.getRuleValue());
                    assertFalse(body.isActive());
                    assertEquals(1, body.getScreenCount(), "now matches the Laval board");
                });
    }

    @Test
    void deleteBundle_withoutSubscriptions_succeeds() {
        Bundle bundle = givenBundle(BundleRuleType.CITY, "Montreal", true);

        webTestClient.delete().uri(BASE_URI + "/" + bundle.getBundleId())
                .header("Authorization", "Bearer " + ADMIN_TOKEN)
                .exchange()
                .expectStatus().isNoContent();

        assertTrue(bundleRepository.findByBundleId(bundle.getBundleId()).isEmpty());
    }

    @Test
    void deleteBundle_withLiveSubscription_is409() {
        Bundle bundle = givenBundle(BundleRuleType.CITY, "Montreal", true);
        givenSubscription(bundle, BundleSubscriptionStatus.ACTIVE);

        webTestClient.delete().uri(BASE_URI + "/" + bundle.getBundleId())
                .header("Authorization", "Bearer " + ADMIN_TOKEN)
                .exchange()
                .expectStatus().isEqualTo(409);

        assertTrue(bundleRepository.findByBundleId(bundle.getBundleId()).isPresent());
    }

    /**
     * D47: canceled history blocks the delete too. Brief req. 5 said such a bundle was deletable
     * and that its history would cascade away — but {@code bundle_subscriptions.bundle_id} has no
     * {@code ON DELETE} clause, so Postgres refuses. Verified against a real migrated schema.
     * This test asserted a 204 before D47 and passed only because the entity-generated test
     * schema has no foreign keys (D5).
     */
    @Test
    void deleteBundle_withOnlyCanceledSubscriptions_isAlsoBlocked() {
        Bundle bundle = givenBundle(BundleRuleType.CITY, "Montreal", true);
        givenSubscription(bundle, BundleSubscriptionStatus.CANCELED);

        webTestClient.delete().uri(BASE_URI + "/" + bundle.getBundleId())
                .header("Authorization", "Bearer " + ADMIN_TOKEN)
                .exchange()
                .expectStatus().isEqualTo(409);

        assertTrue(bundleRepository.findByBundleId(bundle.getBundleId()).isPresent());
    }

    @Test
    void deleteBundle_withNoSubscriptionsAtAll_succeeds() {
        Bundle bundle = givenBundle(BundleRuleType.CITY, "Montreal", true);

        webTestClient.delete().uri(BASE_URI + "/" + bundle.getBundleId())
                .header("Authorization", "Bearer " + ADMIN_TOKEN)
                .exchange()
                .expectStatus().isNoContent();

        assertTrue(bundleRepository.findByBundleId(bundle.getBundleId()).isEmpty());
    }

    /**
     * The count drives the admin delete modal's disabled state and its explanation, so it has to
     * agree with the guard exactly. Since D47 widened the guard to every status, this counts both
     * rows — a bundle showing "1" while the delete fails would be worse than the old wording.
     */
    @Test
    void subscriptionCount_onTheResponse_countsEveryStatus() {
        Bundle bundle = givenBundle(BundleRuleType.CITY, "Montreal", true);
        givenSubscription(bundle, BundleSubscriptionStatus.ACTIVE);
        givenSubscription(bundle, BundleSubscriptionStatus.CANCELED);

        webTestClient.get().uri(BASE_URI + "/" + bundle.getBundleId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(BundleResponseModel.class)
                .value(body -> assertEquals(2, body.getActiveSubscriptionCount()));
    }

    private void givenSubscription(Bundle bundle, BundleSubscriptionStatus status) {
        BundleSubscription subscription = new BundleSubscription();
        subscription.setBundleId(bundle.getBundleId());
        subscription.setAdvertiserBusinessId(UUID.randomUUID().toString());
        subscription.setCampaignId("campaign-1");
        subscription.setStripeCheckoutSessionId("cs_test_" + UUID.randomUUID());
        subscription.setStatus(status);
        subscription.setMonthlyAmount(new BigDecimal("120.00"));
        subscription.setScreenCount(30);
        subscriptionRepository.save(subscription);
    }

    // ---------- exclusions ----------

    @Test
    void candidateMedias_returnsThePreExclusionSetWithFlags() {
        Bundle bundle = givenBundle(BundleRuleType.FULL_NETWORK, null, true);
        excludedMediaRepository.save(new BundleExcludedMedia(bundle.getBundleId(), lavalMedia.getId()));

        webTestClient.get().uri(BASE_URI + "/" + bundle.getBundleId() + "/candidate-medias")
                .header("Authorization", "Bearer " + ADMIN_TOKEN)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(BundleCandidateMediaResponseModel.class)
                .hasSize(2)
                .value(candidates -> {
                    BundleCandidateMediaResponseModel excluded = candidates.stream()
                            .filter(c -> c.getMediaId().equals(lavalMedia.getId()))
                            .findFirst().orElseThrow();
                    BundleCandidateMediaResponseModel kept = candidates.stream()
                            .filter(c -> c.getMediaId().equals(montrealMedia.getId()))
                            .findFirst().orElseThrow();

                    assertTrue(excluded.isExcluded(), "excluded media stays listed, flagged");
                    assertFalse(kept.isExcluded());
                    assertEquals("Laval", excluded.getCity());
                    assertEquals("Laurentides", excluded.getRegion());
                });
    }

    @Test
    void excludingAMedia_dropsItFromScreenCountAndPrice() {
        Bundle bundle = givenBundle(BundleRuleType.FULL_NETWORK, null, true);

        webTestClient.put()
                .uri(BASE_URI + "/" + bundle.getBundleId() + "/excluded-medias/" + lavalMedia.getId())
                .header("Authorization", "Bearer " + ADMIN_TOKEN)
                .exchange()
                .expectStatus().isNoContent();

        webTestClient.get().uri(BASE_URI + "/" + bundle.getBundleId())
                .exchange()
                .expectBody(BundleResponseModel.class)
                .value(body -> {
                    assertEquals(1, body.getScreenCount());
                    assertEquals(0, new BigDecimal("4.00").compareTo(body.getBasePrice()));
                });
    }

    @Test
    void reIncludingAMedia_restoresItToTheQuote() {
        Bundle bundle = givenBundle(BundleRuleType.FULL_NETWORK, null, true);
        excludedMediaRepository.save(new BundleExcludedMedia(bundle.getBundleId(), lavalMedia.getId()));

        webTestClient.delete()
                .uri(BASE_URI + "/" + bundle.getBundleId() + "/excluded-medias/" + lavalMedia.getId())
                .header("Authorization", "Bearer " + ADMIN_TOKEN)
                .exchange()
                .expectStatus().isNoContent();

        webTestClient.get().uri(BASE_URI + "/" + bundle.getBundleId())
                .exchange()
                .expectBody(BundleResponseModel.class)
                .value(body -> assertEquals(2, body.getScreenCount()));
    }

    @Test
    void excludingTwice_isIdempotent() {
        Bundle bundle = givenBundle(BundleRuleType.FULL_NETWORK, null, true);
        String uri = BASE_URI + "/" + bundle.getBundleId() + "/excluded-medias/" + lavalMedia.getId();

        webTestClient.put().uri(uri).header("Authorization", "Bearer " + ADMIN_TOKEN)
                .exchange().expectStatus().isNoContent();
        webTestClient.put().uri(uri).header("Authorization", "Bearer " + ADMIN_TOKEN)
                .exchange().expectStatus().isNoContent();

        assertEquals(1, excludedMediaRepository.findAllByIdBundleId(bundle.getBundleId()).size());
    }

    @Test
    void candidateMedias_withoutPermission_isRejected() {
        Bundle bundle = givenBundle(BundleRuleType.FULL_NETWORK, null, true);
        Jwt plainJwt = Jwt.withTokenValue("plain-token")
                .header("alg", "none")
                .claim("sub", "auth0|user123")
                .claim("permissions", List.of("read:media"))
                .build();
        when(jwtDecoder.decode(anyString())).thenReturn(plainJwt);

        webTestClient.get().uri(BASE_URI + "/" + bundle.getBundleId() + "/candidate-medias")
                .header("Authorization", "Bearer plain-token")
                .exchange()
                .expectStatus().isForbidden();
    }

    // ---------- discount ----------

    @Test
    void discount_appliesToFinalPriceWhileBasePriceStaysUndiscounted() {
        // Montreal bundle = the single $4.00 Downtown board.
        Bundle bundle = givenBundle(BundleRuleType.CITY, "Montreal", true);
        bundle.setDiscountPercent(25);
        bundleRepository.save(bundle);

        webTestClient.get().uri(BASE_URI + "/" + bundle.getBundleId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(BundleResponseModel.class)
                .value(body -> {
                    assertEquals(25, body.getDiscountPercent());
                    assertEquals(0, new BigDecimal("4.00").compareTo(body.getBasePrice()),
                            "basePrice is the undiscounted sum the card strikes through");
                    assertEquals(0, new BigDecimal("3.00").compareTo(body.getFinalPrice()));
                    assertEquals(0, new BigDecimal("4.00").compareTo(body.getPerScreenPrice()));
                    assertEquals(0, new BigDecimal("3.00").compareTo(body.getDiscountedPerScreenPrice()));
                });
    }

    @Test
    void noDiscount_leavesBaseAndFinalEqualAndDiscountedPerScreenNull() {
        Bundle bundle = givenBundle(BundleRuleType.CITY, "Montreal", true);

        webTestClient.get().uri(BASE_URI + "/" + bundle.getBundleId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(BundleResponseModel.class)
                .value(body -> {
                    assertEquals(0, body.getDiscountPercent());
                    assertEquals(0, body.getBasePrice().compareTo(body.getFinalPrice()));
                    assertNull(body.getDiscountedPerScreenPrice());
                });
    }

    @Test
    void discount_isPersistedThroughCreateAndUpdate() {
        BundleRequestModel create = requestModel(BundleRuleType.CITY, "Montreal");
        create.setDiscountPercent(15);

        String bundleId = webTestClient.post().uri(BASE_URI)
                .header("Authorization", "Bearer " + ADMIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(create)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(BundleResponseModel.class)
                .value(body -> assertEquals(15, body.getDiscountPercent()))
                .returnResult().getResponseBody().getBundleId();

        BundleRequestModel update = requestModel(BundleRuleType.CITY, "Montreal");
        update.setDiscountPercent(40);

        webTestClient.put().uri(BASE_URI + "/" + bundleId)
                .header("Authorization", "Bearer " + ADMIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(update)
                .exchange()
                .expectStatus().isOk()
                .expectBody(BundleResponseModel.class)
                .value(body -> assertEquals(40, body.getDiscountPercent()));
    }

    @Test
    void discount_omittedOnUpdateClearsIt() {
        Bundle bundle = givenBundle(BundleRuleType.CITY, "Montreal", true);
        bundle.setDiscountPercent(30);
        bundleRepository.save(bundle);

        // No discountPercent on the body — the admin cleared the field.
        webTestClient.put().uri(BASE_URI + "/" + bundle.getBundleId())
                .header("Authorization", "Bearer " + ADMIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestModel(BundleRuleType.CITY, "Montreal"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(BundleResponseModel.class)
                .value(body -> assertEquals(0, body.getDiscountPercent()));
    }

    @Test
    void discount_outOfRangeIsRejected() {
        BundleRequestModel request = requestModel(BundleRuleType.CITY, "Montreal");
        request.setDiscountPercent(150);

        webTestClient.post().uri(BASE_URI)
                .header("Authorization", "Bearer " + ADMIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();
    }

    // ---------- perScreenPrice on the response ----------

    @Test
    void perScreenPrice_isSetWhenAllEligibleScreensShareOnePrice() {
        Bundle bundle = givenBundle(BundleRuleType.CITY, "Montreal", true);

        webTestClient.get().uri(BASE_URI + "/" + bundle.getBundleId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(BundleResponseModel.class)
                .value(body -> {
                    assertEquals(1, body.getScreenCount());
                    assertEquals(0, new BigDecimal("4.00").compareTo(body.getPerScreenPrice()));
                });
    }

    @Test
    void perScreenPrice_isNullWhenPricesAreMixed() {
        // Full network here = Downtown $4.00 + Laval $6.50.
        Bundle bundle = givenBundle(BundleRuleType.FULL_NETWORK, null, true);

        webTestClient.get().uri(BASE_URI + "/" + bundle.getBundleId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(BundleResponseModel.class)
                .value(body -> {
                    assertEquals(2, body.getScreenCount());
                    assertNull(body.getPerScreenPrice(), "mixed prices must not yield a per-screen figure");
                });
    }

    @Test
    void perScreenPrice_isNullWhenNoEligibleScreens() {
        Bundle bundle = givenBundle(BundleRuleType.CITY, "Vancouver", true);

        webTestClient.get().uri(BASE_URI + "/" + bundle.getBundleId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(BundleResponseModel.class)
                .value(body -> {
                    assertEquals(0, body.getScreenCount());
                    assertNull(body.getPerScreenPrice());
                });
    }

    // ---------- authenticated quote endpoint ----------

    private static final String BUYER_BUSINESS_ID = "buyer-business-1";
    private static final String BUYER_USER_ID = "auth0|buyer123";
    private static final String BUYER_TOKEN = "buyer-token";

    private void givenBuyerEmployee() {
        Employee employee = new Employee();
        employee.setEmployeeId(new EmployeeIdentifier());
        employee.setBusinessId(new BusinessIdentifier(BUYER_BUSINESS_ID));
        employee.setUserId(BUYER_USER_ID);
        employeeRepository.save(employee);
    }

    private void authAs(String token, String userId, List<String> permissions) {
        Jwt jwt = Jwt.withTokenValue(token)
                .header("alg", "none")
                .claim("sub", userId)
                .claim("permissions", permissions)
                .build();
        when(jwtDecoder.decode(anyString())).thenReturn(jwt);
    }

    @Test
    void quote_forAnEmployeeOfTheBusiness_returnsScreenCountAndPrice() {
        Bundle bundle = givenBundle(BundleRuleType.CITY, "Montreal", true);
        givenBuyerEmployee();
        authAs(BUYER_TOKEN, BUYER_USER_ID, List.of("read:campaign"));

        webTestClient.get()
                .uri(BASE_URI + "/" + bundle.getBundleId() + "/quote?businessId=" + BUYER_BUSINESS_ID)
                .header("Authorization", "Bearer " + BUYER_TOKEN)
                .exchange()
                .expectStatus().isOk()
                .expectBody(BundlePriceQuoteResponseModel.class)
                .value(body -> {
                    assertEquals(1, body.getScreenCount());
                    assertEquals(0, new BigDecimal("4.00").compareTo(body.getFinalPrice()));
                    assertEquals(0, new BigDecimal("4.00").compareTo(body.getPerScreenPrice()));
                });
    }

    @Test
    void quote_anonymously_isRejected() {
        Bundle bundle = givenBundle(BundleRuleType.CITY, "Montreal", true);

        webTestClient.get()
                .uri(BASE_URI + "/" + bundle.getBundleId() + "/quote?businessId=" + BUYER_BUSINESS_ID)
                .exchange()
                // Method security rejects an anonymous caller as 401 or 403 depending on
                // the entry point; either is a correct "not allowed", matching the
                // convention in createBundle_anonymously_isRejected.
                .expectStatus().value(status -> assertTrue(status == 401 || status == 403,
                        "anonymous quote must be rejected, got " + status));
    }

    @Test
    void quote_forANonEmployeeOfTheBusiness_isForbidden() {
        Bundle bundle = givenBundle(BundleRuleType.CITY, "Montreal", true);
        // Authenticated, but no employee row links this user to the business.
        authAs(BUYER_TOKEN, BUYER_USER_ID, List.of("read:campaign"));

        webTestClient.get()
                .uri(BASE_URI + "/" + bundle.getBundleId() + "/quote?businessId=" + BUYER_BUSINESS_ID)
                .header("Authorization", "Bearer " + BUYER_TOKEN)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void quote_forAnUnknownBundle_isNotFound() {
        givenBuyerEmployee();
        authAs(BUYER_TOKEN, BUYER_USER_ID, List.of("read:campaign"));

        webTestClient.get()
                .uri(BASE_URI + "/no-such-bundle/quote?businessId=" + BUYER_BUSINESS_ID)
                .header("Authorization", "Bearer " + BUYER_TOKEN)
                .exchange()
                .expectStatus().isNotFound();
    }
}
