package com.envisionad.webservice.advertisement.businesslogiclayer;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.cloudinary.api.ApiResponse;
import com.cloudinary.api.RateLimit;
import com.envisionad.webservice.advertisement.dataaccesslayer.*;
import com.envisionad.webservice.advertisement.datamapperlayer.AdRequestMapper;
import com.envisionad.webservice.advertisement.datamapperlayer.AdResponseMapper;
import com.envisionad.webservice.advertisement.datamapperlayer.AdCampaignResponseMapper;
import com.envisionad.webservice.advertisement.exceptions.*;
import com.envisionad.webservice.advertisement.presentationlayer.models.AdRequestModel;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionRepository;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionStatus;
import com.envisionad.webservice.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.springframework.security.oauth2.jwt.Jwt;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class AdCampaignServiceUnitTest {

    @Mock private AdCampaignRepository adCampaignRepository;
    @Mock private AdRequestMapper adRequestMapper;
    @Mock private AdResponseMapper adResponseMapper;
    @Mock private AdCampaignResponseMapper adCampaignResponseMapper;
    @Mock private BundleSubscriptionRepository bundleSubscriptionRepository;

    @Mock private Cloudinary cloudinary;
    @Mock private Uploader uploader;
    @Mock private com.cloudinary.Api api;
    @Mock private JwtUtils jwtUtils;

    @InjectMocks private AdCampaignServiceImpl service;

    private Jwt advertiserToken;
    @BeforeEach
    void setUp() {
        lenient().when(cloudinary.uploader()).thenReturn(uploader);
        lenient().when(cloudinary.api()).thenReturn(api);
        advertiserToken = createJwtToken(
                List.of("read:campaign", "create:campaign", "update:campaign", "update:business",
                        "read:employee", "create:employee", "delete:employee", "read:verification", "create:verification",
                        "delete:campaign"));

    }

    private Jwt createJwtToken(List<String> permissions) {
        return Jwt.withTokenValue("advertiser-token")
                .header("alg", "none")
                .claim("sub", "auth0|696a88eb347945897ef17093")
                .claim("scope", "read write")
                .claim("permissions", permissions)
                .build();
    }

    @Test
    void getActiveCampaignCount_shouldReturnCount() {

        // Arrange
        String businessId = "business-123";
        Integer expectedCount = 5;

        when(bundleSubscriptionRepository.countDistinctCampaignsByAdvertiserBusinessIdAndStatusIn(
                eq(businessId),
                any()
        )).thenReturn(expectedCount);

        // Act
        Integer result = service.getActiveCampaignCount(businessId);

        // Assert
        assertEquals(expectedCount, result);

        verify(bundleSubscriptionRepository, times(1))
                .countDistinctCampaignsByAdvertiserBusinessIdAndStatusIn(eq(businessId), any());
    }


    @Test
    void deleteAdFromCampaign_whenUrlIsNull_doesNotCallCloudinary_andDeletesAd() throws IOException {

        // Arrange
        String campaignId = "camp-1";
        CampaignAndAdId data = campaignWithSingleAd(campaignId, null);

        when(adCampaignRepository.findByCampaignId_CampaignId(campaignId))
                .thenReturn(data.campaign);
        when(adCampaignRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(adResponseMapper.entityToResponseModel(any()))
                .thenReturn(null);

        // Act
        service.deleteAdFromCampaign(campaignId, data.adId);

        // Assert
        verify(uploader, never()).destroy(anyString(), anyMap());
        verify(adCampaignRepository).save(data.campaign);
        assertEquals(0, data.campaign.getAds().size());
    }

    @Test
    void deleteAdFromCampaign_whenUrlIsBlank_doesNotCallCloudinary_andDeletesAd() throws IOException {

        // Arrange
        String campaignId = "camp-1";
        CampaignAndAdId data = campaignWithSingleAd(campaignId, "   ");

        when(adCampaignRepository.findByCampaignId_CampaignId(campaignId))
                .thenReturn(data.campaign);
        when(adCampaignRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(adResponseMapper.entityToResponseModel(any()))
                .thenReturn(null);

        // Act
        service.deleteAdFromCampaign(campaignId, data.adId);

        // Assert
        verify(uploader, never()).destroy(anyString(), anyMap());
        verify(adCampaignRepository).save(data.campaign);
        assertEquals(0, data.campaign.getAds().size());
    }

    @Test
    void deleteAdFromCampaign_whenValidImageUrl_callsCloudinaryDestroy() throws Exception {

        // Arrange
        String campaignId = "camp-1";
        String url = "https://res.cloudinary.com/demo/image/upload/v12345/envisionad/ads/banner_01.jpg";
        CampaignAndAdId data = campaignWithSingleAd(campaignId, url);

        when(adCampaignRepository.findByCampaignId_CampaignId(campaignId))
                .thenReturn(data.campaign);
        when(adCampaignRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(adResponseMapper.entityToResponseModel(any()))
                .thenReturn(null);

        when(uploader.destroy(anyString(), anyMap()))
                .thenReturn(Map.of("result", "ok"));

        // Act
        service.deleteAdFromCampaign(campaignId, data.adId);

        // Assert
        verify(uploader).destroy(anyString(), argThat(opts ->
                Boolean.TRUE.equals(opts.get("invalidate")) &&
                        opts.get("resource_type") != null
        ));
        verify(adCampaignRepository).save(data.campaign);
        assertEquals(0, data.campaign.getAds().size());
    }

    @Test
    void deleteAdFromCampaign_whenUrlHasLeadingTrailingWhitespace_stillCallsCloudinaryDestroy() throws Exception {

        // Arrange
        String campaignId = "camp-1";
        String urlWithSpaces =
                " https://res.cloudinary.com/demo/image/upload/v12345/envisionad/ads/banner_01.jpg ";

        CampaignAndAdId data = campaignWithSingleAd(campaignId, urlWithSpaces);

        when(adCampaignRepository.findByCampaignId_CampaignId(campaignId))
                .thenReturn(data.campaign);
        when(adCampaignRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(adResponseMapper.entityToResponseModel(any()))
                .thenReturn(null);

        when(uploader.destroy(anyString(), anyMap()))
                .thenReturn(Map.of("result", "ok"));

        // Act
        service.deleteAdFromCampaign(campaignId, data.adId);

        // Assert
        ArgumentCaptor<String> publicIdCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> optionsCaptor = ArgumentCaptor.forClass(Map.class);

        verify(uploader).destroy(publicIdCaptor.capture(), optionsCaptor.capture());

        assertNotNull(publicIdCaptor.getValue());
        assertFalse(publicIdCaptor.getValue().isBlank());

        Map<String, Object> opts = optionsCaptor.getValue();
        assertEquals(true, opts.get("invalidate"));
        assertNotNull(opts.get("resource_type"));

        verify(adCampaignRepository).save(data.campaign);
        assertEquals(0, data.campaign.getAds().size());
    }

    @Test
    void deleteAdFromCampaign_whenUrlHasTransformations_stillDeletesAsset() throws Exception {

        // Arrange
        String campaignId = "camp-1";
        String url = "https://res.cloudinary.com/demo/image/upload/c_fill,w_800,h_400,q_auto,f_auto/v9999/envisionad/ads/banner_02.png";
        CampaignAndAdId data = campaignWithSingleAd(campaignId, url);

        when(adCampaignRepository.findByCampaignId_CampaignId(campaignId))
                .thenReturn(data.campaign);
        when(adCampaignRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(adResponseMapper.entityToResponseModel(any()))
                .thenReturn(null);

        when(uploader.destroy(anyString(), anyMap()))
                .thenReturn(Map.of("result", "ok"));

        // Act
        service.deleteAdFromCampaign(campaignId, data.adId);

        // Assert
        verify(uploader).destroy(anyString(), argThat(opts ->
                Boolean.TRUE.equals(opts.get("invalidate")) &&
                        opts.get("resource_type") != null
        ));
    }

    @Test
    void deleteAdFromCampaign_whenVideoUrl_setsResourceTypeVideo() throws Exception {

        // Arrange
        String campaignId = "camp-1";
        String url = "https://res.cloudinary.com/demo/video/upload/v12345/envisionad/ads/spot_01.mp4";
        CampaignAndAdId data = campaignWithSingleAd(campaignId, url);

        when(adCampaignRepository.findByCampaignId_CampaignId(campaignId))
                .thenReturn(data.campaign);
        when(adCampaignRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(adResponseMapper.entityToResponseModel(any()))
                .thenReturn(null);

        when(uploader.destroy(anyString(), anyMap()))
                .thenReturn(Map.of("result", "ok"));

        // Act
        service.deleteAdFromCampaign(campaignId, data.adId);

        // Assert
        verify(uploader).destroy(anyString(), argThat(opts ->
                Boolean.TRUE.equals(opts.get("invalidate")) &&
                        "video".equals(opts.get("resource_type"))
        ));
    }

    @Test
    void deleteAdFromCampaign_whenCloudinaryFails_stillDeletesAd() throws Exception {

        // Arrange
        String campaignId = "camp-1";
        String url = "https://res.cloudinary.com/demo/image/upload/v12345/envisionad/ads/banner_03.jpg";
        CampaignAndAdId data = campaignWithSingleAd(campaignId, url);

        when(adCampaignRepository.findByCampaignId_CampaignId(campaignId))
                .thenReturn(data.campaign);
        when(adCampaignRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(adResponseMapper.entityToResponseModel(any()))
                .thenReturn(null);

        when(uploader.destroy(anyString(), anyMap()))
                .thenThrow(new RuntimeException("Cloudinary down"));

        // Act
        service.deleteAdFromCampaign(campaignId, data.adId);

        // Assert
        verify(uploader, times(1)).destroy(anyString(), anyMap());
        verify(adCampaignRepository).save(data.campaign);
        assertEquals(0, data.campaign.getAds().size());

    }

    @Test
    void deleteAdFromCampaign_whenCampaignNotFound_throwsException() throws IOException {

        // Arrange
        String campaignId = "missing";
        String adId = UUID.randomUUID().toString();

        when(adCampaignRepository.findByCampaignId_CampaignId(campaignId))
                .thenReturn(null);

        // Act & Assert
        assertThrows(AdCampaignNotFoundException.class,
                () -> service.deleteAdFromCampaign(campaignId, adId));

        verify(uploader, never()).destroy(anyString(), anyMap());
        verify(adCampaignRepository, never()).save(any());
    }

    @Test
    void deleteAdFromCampaign_whenAdNotFound_throwsException() throws IOException {

        // Arrange
        String campaignId = "camp-1";
        String missingAdId = UUID.randomUUID().toString();

        AdCampaign campaign = new AdCampaign();
        campaign.setCampaignId(new AdCampaignIdentifier(campaignId));
        campaign.setAds(new ArrayList<>());

        when(adCampaignRepository.findByCampaignId_CampaignId(campaignId))
                .thenReturn(campaign);

        // Act & Assert
        assertThrows(AdNotFoundException.class,
                () -> service.deleteAdFromCampaign(campaignId, missingAdId));

        verify(uploader, never()).destroy(anyString(), anyMap());
        verify(adCampaignRepository, never()).save(any());
    }

    // ---------------- Helpers ----------------

    /** Minimal ApiResponse (Map + the 2 rate-limit accessors) so tests can stub cloudinary.api()
     * without pulling in the real HTTP client. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static class FakeApiResponse extends HashMap implements ApiResponse {
        @Override public Map<String, RateLimit> rateLimits() { return Map.of(); }
        @Override public RateLimit apiRateLimit() { return null; }
    }

    private static ApiResponse apiResponseWithDuration(double durationSeconds) {
        FakeApiResponse response = new FakeApiResponse();
        response.put("duration", durationSeconds);
        return response;
    }

    private static class CampaignAndAdId {
        final AdCampaign campaign;
        final String adId;
        CampaignAndAdId(AdCampaign campaign, String adId) {
            this.campaign = campaign;
            this.adId = adId;
        }
    }

    private CampaignAndAdId campaignWithSingleAd(String campaignId, String url) {
        AdCampaign campaign = new AdCampaign();
        campaign.setCampaignId(new AdCampaignIdentifier(campaignId));

        Ad ad = new Ad();
        String id = UUID.randomUUID().toString();
        ad.setAdIdentifier(new AdIdentifier(id));
        ad.setAdUrl(url);
        ad.setCampaign(campaign);

        campaign.setAds(new ArrayList<>(List.of(ad)));

        return new CampaignAndAdId(campaign, id);
    }

    @Test
    void deleteAdCampaign_whenNoSubscriptionReferencesIt_deletesSuccessfully() {
        // Arrange
        String businessId = "biz-1";
        String campaignId = "camp-1";
        AdCampaign campaign = new AdCampaign();
        campaign.setCampaignId(new AdCampaignIdentifier(campaignId));
        campaign.setAds(new ArrayList<>());

        when(adCampaignRepository.findByCampaignId_CampaignId(campaignId)).thenReturn(campaign);
        doNothing().when(jwtUtils).validateUserIsEmployeeOfBusiness(any(Jwt.class), eq(businessId));
        doNothing().when(jwtUtils).validateBusinessOwnsCampaign(eq(businessId), eq(campaign));

        when(bundleSubscriptionRepository.existsByCampaignId(eq(campaignId)))
                .thenReturn(false);

        when(adCampaignResponseMapper.entityToResponseModel(campaign)).thenReturn(null);

        // Act
        service.deleteAdCampaign(advertiserToken, businessId, campaignId);

        // Assert
        verify(adCampaignRepository).delete(campaign);
        verify(adCampaignResponseMapper).entityToResponseModel(campaign);
    }

    @Test
    void deleteAdCampaign_whenNoSubscriptionReferencesItAndHasCloudinaryAds_deletesAssetsAndCampaign() throws IOException {
        // Arrange
        String businessId = "biz-1";
        String campaignId = "camp-cloudinary-1";
        String cloudinaryUrl = "https://res.cloudinary.com/demo/image/upload/v1234567/sample-public-id.jpg";

        CampaignAndAdId campaignAndAdId = campaignWithSingleAd(campaignId, cloudinaryUrl);
        AdCampaign campaignWithAd = campaignAndAdId.campaign;

        when(adCampaignRepository.findByCampaignId_CampaignId(campaignId)).thenReturn(campaignWithAd);
        doNothing().when(jwtUtils).validateUserIsEmployeeOfBusiness(any(Jwt.class), eq(businessId));
        doNothing().when(jwtUtils).validateBusinessOwnsCampaign(eq(businessId), eq(campaignWithAd));

        when(bundleSubscriptionRepository.existsByCampaignId(eq(campaignId)))
                .thenReturn(false);

        when(uploader.destroy(anyString(), anyMap())).thenReturn(Map.of("result", "ok"));

        when(adCampaignResponseMapper.entityToResponseModel(campaignWithAd)).thenReturn(null);

        // Act
        service.deleteAdCampaign(advertiserToken, businessId, campaignId);

        // Assert
        verify(adCampaignRepository).delete(campaignWithAd);
        verify(adCampaignResponseMapper).entityToResponseModel(campaignWithAd);
        // Verify that the Cloudinary asset tied to the ad was scheduled for deletion
        verify(uploader, atLeastOnce()).destroy(anyString(), anyMap());
    }

    @Test
    void deleteAdCampaign_whenASubscriptionReferencesIt_throwsException() {
        // Arrange
        String businessId = "biz-1";
        String campaignId = "camp-2";
        AdCampaign campaign = new AdCampaign();
        campaign.setCampaignId(new AdCampaignIdentifier(campaignId));
        campaign.setAds(new ArrayList<>());

        when(adCampaignRepository.findByCampaignId_CampaignId(campaignId)).thenReturn(campaign);
        doNothing().when(jwtUtils).validateUserIsEmployeeOfBusiness(any(Jwt.class), eq(businessId));
        doNothing().when(jwtUtils).validateBusinessOwnsCampaign(eq(businessId), eq(campaign));

        when(bundleSubscriptionRepository.existsByCampaignId(eq(campaignId)))
                .thenReturn(true);

        // Act & Assert
        assertThrows(CampaignIsTiedToSubscriptionException.class,
            () -> service.deleteAdCampaign(advertiserToken, businessId, campaignId));
        verify(adCampaignRepository, never()).delete(any());
    }

    /**
     * D47: a CANCELED subscription blocks the delete just as firmly as a live one. The guard is
     * status-agnostic because it mirrors the ON DELETE RESTRICT foreign key — cancelling does not
     * release the campaign, since the subscription's billing history still points at it.
     */
    @Test
    void deleteAdCampaign_whenOnlyTiedToACanceledSubscription_stillThrows() {
        // Arrange
        String businessId = "biz-1";
        String campaignId = "camp-4";
        AdCampaign campaign = new AdCampaign();
        campaign.setCampaignId(new AdCampaignIdentifier(campaignId));
        campaign.setAds(new ArrayList<>());

        when(adCampaignRepository.findByCampaignId_CampaignId(campaignId)).thenReturn(campaign);
        doNothing().when(jwtUtils).validateUserIsEmployeeOfBusiness(any(Jwt.class), eq(businessId));
        doNothing().when(jwtUtils).validateBusinessOwnsCampaign(eq(businessId), eq(campaign));

        // The row is CANCELED, but the guard no longer asks — any row at all returns true.
        when(bundleSubscriptionRepository.existsByCampaignId(eq(campaignId)))
                .thenReturn(true);

        // Act & Assert
        assertThrows(CampaignIsTiedToSubscriptionException.class,
                () -> service.deleteAdCampaign(advertiserToken, businessId, campaignId));
        verify(adCampaignRepository, never()).delete(any());
    }

    /**
     * D42: adding an ad to a campaign that is running on a live bundle subscription must
     * SUCCEED. Under weekly reservations this was blocked; under a monthly subscription the
     * advertiser is paying continuously and has to be able to change their creative mid-cycle.
     * Asserted explicitly so that re-introducing the guard fails the build rather than passing
     * silently.
     */
    @Test
    void addAdToCampaign_whenCampaignHasLiveSubscription_stillAddsTheAd() {
        // Arrange
        String campaignId = "camp-subscribed-1";

        AdCampaign campaign = new AdCampaign();
        campaign.setCampaignId(new AdCampaignIdentifier(campaignId));
        campaign.setAds(new ArrayList<>());

        when(adCampaignRepository.findByCampaignId_CampaignId(campaignId))
                .thenReturn(campaign);
        when(adRequestMapper.requestModelToEntity(any())).thenReturn(new Ad());
        when(adCampaignRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(adResponseMapper.entityToResponseModel(any())).thenReturn(null);

        AdRequestModel adRequestModel = mock(AdRequestModel.class);
        when(adRequestModel.getAdType()).thenReturn("IMAGE");

        // Act
        service.addAdToCampaign(campaignId, adRequestModel);

        // Assert — the ad landed, and the subscription state was never even consulted.
        verify(adCampaignRepository).save(campaign);
        assertEquals(1, campaign.getAds().size());
        verify(bundleSubscriptionRepository, never()).existsByCampaignId(any());
    }

    @Test
    void addAdToCampaign_whenVideoWithinLimit_addsTheAd() throws Exception {
        // Arrange
        String campaignId = "camp-video-ok";
        AdCampaign campaign = new AdCampaign();
        campaign.setCampaignId(new AdCampaignIdentifier(campaignId));
        campaign.setAds(new ArrayList<>());

        Ad videoAd = new Ad();
        videoAd.setAdUrl("https://res.cloudinary.com/demo/video/upload/v12345/envisionad/ads/spot_ok.mp4");

        when(adCampaignRepository.findByCampaignId_CampaignId(campaignId)).thenReturn(campaign);
        when(adRequestMapper.requestModelToEntity(any())).thenReturn(videoAd);
        when(adCampaignRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(adResponseMapper.entityToResponseModel(any())).thenReturn(null);
        when(api.resource(anyString(), anyMap())).thenReturn(apiResponseWithDuration(20.0));

        AdRequestModel adRequestModel = mock(AdRequestModel.class);
        when(adRequestModel.getAdType()).thenReturn("VIDEO");

        // Act
        service.addAdToCampaign(campaignId, adRequestModel);

        // Assert
        verify(adCampaignRepository).save(campaign);
        assertEquals(1, campaign.getAds().size());
    }

    @Test
    void addAdToCampaign_whenVideoExceedsLimit_throwsAndDoesNotAddTheAd() throws Exception {
        // Arrange
        String campaignId = "camp-video-too-long";
        AdCampaign campaign = new AdCampaign();
        campaign.setCampaignId(new AdCampaignIdentifier(campaignId));
        campaign.setAds(new ArrayList<>());

        Ad videoAd = new Ad();
        videoAd.setAdUrl("https://res.cloudinary.com/demo/video/upload/v12345/envisionad/ads/spot_long.mp4");

        when(adCampaignRepository.findByCampaignId_CampaignId(campaignId)).thenReturn(campaign);
        when(adRequestMapper.requestModelToEntity(any())).thenReturn(videoAd);
        when(api.resource(anyString(), anyMap())).thenReturn(apiResponseWithDuration(45.0));

        AdRequestModel adRequestModel = mock(AdRequestModel.class);
        when(adRequestModel.getAdType()).thenReturn("VIDEO");

        // Act & Assert
        assertThrows(VideoTooLongException.class, () -> service.addAdToCampaign(campaignId, adRequestModel));
        verify(adCampaignRepository, never()).save(any());
        assertEquals(0, campaign.getAds().size());
    }

    @Test
    void addAdToCampaign_whenCloudinaryDurationLookupFails_stillAddsTheAd() throws Exception {
        // Arrange — a Cloudinary Admin API hiccup is a business-rule guard, not a security
        // boundary, so a lookup failure must fail open rather than block ad creation.
        String campaignId = "camp-video-lookup-fails";
        AdCampaign campaign = new AdCampaign();
        campaign.setCampaignId(new AdCampaignIdentifier(campaignId));
        campaign.setAds(new ArrayList<>());

        Ad videoAd = new Ad();
        videoAd.setAdUrl("https://res.cloudinary.com/demo/video/upload/v12345/envisionad/ads/spot_unknown.mp4");

        when(adCampaignRepository.findByCampaignId_CampaignId(campaignId)).thenReturn(campaign);
        when(adRequestMapper.requestModelToEntity(any())).thenReturn(videoAd);
        when(adCampaignRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(adResponseMapper.entityToResponseModel(any())).thenReturn(null);
        when(api.resource(anyString(), anyMap())).thenThrow(new RuntimeException("Cloudinary down"));

        AdRequestModel adRequestModel = mock(AdRequestModel.class);
        when(adRequestModel.getAdType()).thenReturn("VIDEO");

        // Act
        service.addAdToCampaign(campaignId, adRequestModel);

        // Assert
        verify(adCampaignRepository).save(campaign);
        assertEquals(1, campaign.getAds().size());
    }

    /**
     * D42, the removal side: deleting an ad from a subscribed campaign must also succeed.
     */
    @Test
    void deleteAdFromCampaign_whenCampaignHasLiveSubscription_stillDeletesTheAd() throws IOException {
        // Arrange
        String campaignId = "camp-subscribed-2";
        CampaignAndAdId data = campaignWithSingleAd(campaignId, null);

        when(adCampaignRepository.findByCampaignId_CampaignId(campaignId))
                .thenReturn(data.campaign);
        when(adCampaignRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(adResponseMapper.entityToResponseModel(any())).thenReturn(null);

        // Act
        service.deleteAdFromCampaign(campaignId, data.adId);

        // Assert
        verify(adCampaignRepository).save(data.campaign);
        assertEquals(0, data.campaign.getAds().size());
        verify(bundleSubscriptionRepository, never()).existsByCampaignId(any());
    }
}
