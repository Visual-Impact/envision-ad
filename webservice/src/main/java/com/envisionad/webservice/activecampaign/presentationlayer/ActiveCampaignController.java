package com.envisionad.webservice.activecampaign.presentationlayer;

import com.envisionad.webservice.activecampaign.businesslogiclayer.ActiveCampaignService;
import com.envisionad.webservice.activecampaign.presentationlayer.models.ActiveCampaignRequestModel;
import com.envisionad.webservice.activecampaign.presentationlayer.models.ActiveCampaignSummaryModel;
import com.envisionad.webservice.activecampaign.presentationlayer.models.NotificationResultModel;
import com.envisionad.webservice.advertisement.presentationlayer.models.AdCampaignResponseModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/v1/")
@CrossOrigin(origins = {"http://localhost:3000", "https://envision-ad.ca"})
public class ActiveCampaignController {

    private final ActiveCampaignService activeCampaignService;

    public ActiveCampaignController(ActiveCampaignService activeCampaignService) {
        this.activeCampaignService = activeCampaignService;
    }

    /** 204 when nothing is displayed yet — an absent slot, not an error. */
    @PreAuthorize("hasAuthority('readAll:campaign')")
    @GetMapping("businesses/{businessId}/active-campaign")
    public ResponseEntity<ActiveCampaignSummaryModel> getActiveCampaign(@PathVariable String businessId) {
        ActiveCampaignSummaryModel summary = activeCampaignService.getActiveCampaignSummary(businessId);
        return summary == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(summary);
    }

    /**
     * The mandatory first pick, called by the checkout flow. Separate from the swap below so
     * that "choose what to display for the first time" and "change what is displayed" cannot be
     * confused: only the latter emails anyone or consumes the cooldown.
     */
    @PreAuthorize("hasAuthority('update:campaign')")
    @PostMapping("businesses/{businessId}/active-campaign/select")
    public ResponseEntity<ActiveCampaignSummaryModel> selectActiveCampaign(
            @PathVariable String businessId,
            @RequestBody ActiveCampaignRequestModel request) {
        return ResponseEntity.ok(
                activeCampaignService.selectInitialActiveCampaign(businessId, request.getCampaignId()));
    }

    @PreAuthorize("hasAuthority('update:campaign')")
    @PutMapping("businesses/{businessId}/active-campaign")
    public ResponseEntity<ActiveCampaignSummaryModel> swapActiveCampaign(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String businessId,
            @RequestBody ActiveCampaignRequestModel request) {
        return ResponseEntity.ok(
                activeCampaignService.swapActiveCampaign(jwt, businessId, request.getCampaignId()));
    }

    @PreAuthorize("hasAuthority('update:campaign')")
    @PostMapping("businesses/{businessId}/active-campaign/notify")
    public ResponseEntity<NotificationResultModel> notifyMediaOwners(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String businessId) {
        return ResponseEntity.ok(activeCampaignService.notifyMediaOwners(jwt, businessId));
    }

    @PreAuthorize("hasAuthority('readAll:campaign')")
    @GetMapping("businesses/{businessId}/campaigns/eligible-for-swap")
    public ResponseEntity<List<AdCampaignResponseModel>> getCampaignsEligibleForSwap(
            @PathVariable String businessId) {
        return ResponseEntity.ok(activeCampaignService.getCampaignsEligibleForSwap(businessId));
    }
}
