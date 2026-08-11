package com.envisionad.webservice.payment.presentationlayer;

import com.envisionad.webservice.payment.businesslogiclayer.BundleSubscriptionService;
import com.envisionad.webservice.payment.businesslogiclayer.SubscriptionCheckoutResult;
import com.envisionad.webservice.payment.presentationlayer.models.BundleSubscriptionCheckoutResponseModel;
import com.envisionad.webservice.payment.presentationlayer.models.BundleSubscriptionRequestModel;
import com.envisionad.webservice.payment.presentationlayer.models.BundleSubscriptionResponseModel;
import com.envisionad.webservice.payment.presentationlayer.models.LiveCampaignResponseModel;
import com.stripe.exception.StripeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bundle-subscriptions")
@CrossOrigin(origins = {"http://localhost:3000", "https://envision-ad.ca"})
public class BundleSubscriptionController {

    private final BundleSubscriptionService bundleSubscriptionService;

    public BundleSubscriptionController(BundleSubscriptionService bundleSubscriptionService) {
        this.bundleSubscriptionService = bundleSubscriptionService;
    }

    /**
     * Authorization is {@code isAuthenticated()} plus the business-employee check inside
     * the service, matching PaymentController and the bundle quote endpoint rather than
     * introducing a new Auth0 permission.
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BundleSubscriptionCheckoutResponseModel> createBundleSubscription(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody BundleSubscriptionRequestModel request) throws StripeException {

        SubscriptionCheckoutResult result = bundleSubscriptionService.createSubscriptionCheckout(
                jwt,
                request.getBundleId(),
                request.getCampaignId(),
                request.getBusinessId(),
                request.getCouponCode());

        return ResponseEntity.status(HttpStatus.CREATED).body(
                new BundleSubscriptionCheckoutResponseModel(
                        result.clientSecret(), result.sessionId(), result.subscriptionId()));
    }

    /**
     * The advertiser's own subscriptions. Returns every status — an INCOMPLETE row from an
     * abandoned checkout still occupies that business's slot for the bundle and has to be
     * visible before it can be cleared.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<BundleSubscriptionResponseModel>> getBundleSubscriptions(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String businessId) {

        return ResponseEntity.ok(bundleSubscriptionService.getSubscriptionsForBusiness(jwt, businessId));
    }

    /**
     * Campaigns currently running on one screen, for the media owner's proof-of-display picker
     * (decision D40). Replaces the old client-side derivation from that screen's reservations.
     */
    @GetMapping("/live-campaigns")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<LiveCampaignResponseModel>> getLiveCampaigns(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String mediaId) {

        return ResponseEntity.ok(bundleSubscriptionService.getLiveCampaignsForMedia(jwt, mediaId));
    }

    /**
     * Cancels at the end of the current billing period, never immediately. Ownership
     * is validated in the service against the subscription's own advertiser business.
     */
    @PostMapping("/{subscriptionId}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> cancelBundleSubscription(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String subscriptionId) throws StripeException {

        bundleSubscriptionService.cancelSubscription(jwt, subscriptionId);
        return ResponseEntity.noContent().build();
    }
}
