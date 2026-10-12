package com.envisionad.webservice.proofofdisplay.presentationlayer;

import com.envisionad.webservice.proofofdisplay.businesslogiclayer.ProofOfDisplayLinkService;
import com.envisionad.webservice.proofofdisplay.presentationlayer.models.MediaOwnerProofSubscriptionResponseModel;
import com.envisionad.webservice.proofofdisplay.presentationlayer.models.ProofOfDisplayLinkRequestModel;
import com.envisionad.webservice.proofofdisplay.presentationlayer.models.ProofOfDisplayLinkResponseModel;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Proof of display as a link (P12). Authorization is {@code isAuthenticated()} plus the business
 * checks inside the service, matching the bundle-subscription endpoints. The advertiser side has
 * no endpoint here: the link rides on {@code GET /api/v1/bundle-subscriptions}.
 */
@RestController
@RequestMapping("/api/v1/proof-of-display")
@CrossOrigin(origins = { "http://localhost:3000", "https://envision-ad.ca" })
public class ProofOfDisplayController {

    private final ProofOfDisplayLinkService linkService;

    public ProofOfDisplayController(ProofOfDisplayLinkService linkService) {
        this.linkService = linkService;
    }

    /** Every subscription covering this media owner's screens, with its link status. */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/media-owner/{businessId}/subscriptions")
    public ResponseEntity<List<MediaOwnerProofSubscriptionResponseModel>> getMediaOwnerSubscriptions(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String businessId) {
        return ResponseEntity.ok(linkService.getSubscriptionsForMediaOwner(jwt, businessId));
    }

    /** Create or replace a subscription's link. Only its sole media owner may, while it runs. */
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/subscriptions/{subscriptionId}/link")
    public ResponseEntity<ProofOfDisplayLinkResponseModel> upsertLink(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String subscriptionId,
            @Valid @RequestBody ProofOfDisplayLinkRequestModel request) {
        return ResponseEntity.ok(linkService.upsertLink(jwt, subscriptionId, request.getUrl()));
    }
}
