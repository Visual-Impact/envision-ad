package com.envisionad.webservice.advertisement.presentationlayer;

import com.envisionad.webservice.advertisement.businesslogiclayer.AdCampaignService;
import com.envisionad.webservice.advertisement.presentationlayer.models.AdCampaignRequestModel;
import com.envisionad.webservice.advertisement.presentationlayer.models.AdCampaignResponseModel;
import com.envisionad.webservice.advertisement.presentationlayer.models.AdRequestModel;
import com.envisionad.webservice.advertisement.presentationlayer.models.AdResponseModel;
import com.envisionad.webservice.advertisement.presentationlayer.models.AdVenueTagsRequestModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/v1/")
@CrossOrigin(origins = {"http://localhost:3000", "https://envision-ad.ca"})
public class AdCampaignController {
    private final AdCampaignService adCampaignService;

    public AdCampaignController(AdCampaignService adCampaignService) {
        this.adCampaignService = adCampaignService;
    }

    @GetMapping("businesses/{businessId}/campaigns")
    @PreAuthorize("hasAuthority('readAll:campaign')")
    public ResponseEntity<List<AdCampaignResponseModel>> getAllBusinessCampaigns(@PathVariable String businessId) {
        return ResponseEntity.ok(adCampaignService.getAllAdCampaignsByBusinessId(businessId));
    }

    @GetMapping("campaigns/{campaignId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AdCampaignResponseModel> getAdCampaignByCampaignId(@PathVariable String campaignId) {
        return ResponseEntity.ok(adCampaignService.getAdCampaignByCampaignId(campaignId));
    }

    @PostMapping("businesses/{businessId}/campaigns")
    @PreAuthorize("hasAuthority('create:campaign')")
    public ResponseEntity<AdCampaignResponseModel> createAdCampaign(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String businessId,
            @RequestBody AdCampaignRequestModel adCampaignRequestModel
            ) {
        AdCampaignResponseModel newCampaign = adCampaignService.createAdCampaign(jwt, businessId, adCampaignRequestModel);

        return ResponseEntity.status(HttpStatus.CREATED).body(newCampaign);
    }

    @PostMapping("businesses/{businessId}/campaigns/{campaignId}/ads")
    @PreAuthorize("hasAuthority('update:campaign')")
    public ResponseEntity<AdResponseModel> addAdToCampaign(
            @PathVariable String campaignId,
            @RequestBody AdRequestModel adRequestModel) {
        AdResponseModel newAd = adCampaignService.addAdToCampaign(campaignId, adRequestModel);

        return ResponseEntity.status(HttpStatus.CREATED).body(newAd);

    }

    @DeleteMapping("businesses/{businessId}/campaigns/{campaignId}/ads/{adId}")
    @PreAuthorize("hasAuthority('update:campaign')")
    public ResponseEntity<AdResponseModel> deleteAdFromCampaign(
            @PathVariable String campaignId,
            @PathVariable String adId) {
        AdResponseModel deletedAd = adCampaignService.deleteAdFromCampaign(campaignId, adId);

        return ResponseEntity.ok(deletedAd);
    }

    @PutMapping("businesses/{businessId}/campaigns/{campaignId}/ads/{adId}/venue-tags")
    @PreAuthorize("hasAuthority('update:campaign')")
    public ResponseEntity<AdResponseModel> updateAdVenueTags(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String businessId,
            @PathVariable String campaignId,
            @PathVariable String adId,
            @RequestBody AdVenueTagsRequestModel adVenueTagsRequestModel) {
        AdResponseModel updatedAd = adCampaignService.updateAdVenueTags(
                jwt, businessId, campaignId, adId, adVenueTagsRequestModel.getVenueIds());

        return ResponseEntity.ok(updatedAd);
    }

    @GetMapping("businesses/{businessId}/campaigns/active-count")
    @PreAuthorize("hasAuthority('readAll:campaign')")
    public ResponseEntity<Integer> getActiveCampaignCount(@PathVariable String businessId) {
        return ResponseEntity.ok(adCampaignService.getActiveCampaignCount(businessId));
    }

    @DeleteMapping("businesses/{businessId}/campaigns/{campaignId}")
    @PreAuthorize("hasAuthority('delete:campaign')")
    public ResponseEntity<AdCampaignResponseModel> deleteCampaign(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String businessId,
            @PathVariable String campaignId) {
        return ResponseEntity.ok(adCampaignService.deleteAdCampaign(jwt, businessId, campaignId));
    }
}
