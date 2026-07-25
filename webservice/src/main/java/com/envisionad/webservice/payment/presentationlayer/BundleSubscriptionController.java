package com.envisionad.webservice.payment.presentationlayer;

import com.envisionad.webservice.payment.businesslogiclayer.BundleSubscriptionService;
import com.envisionad.webservice.payment.businesslogiclayer.SubscriptionCheckoutResult;
import com.envisionad.webservice.payment.presentationlayer.models.BundleSubscriptionCheckoutResponseModel;
import com.envisionad.webservice.payment.presentationlayer.models.BundleSubscriptionRequestModel;
import com.stripe.exception.StripeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

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
                request.getBusinessId());

        return ResponseEntity.status(HttpStatus.CREATED).body(
                new BundleSubscriptionCheckoutResponseModel(
                        result.clientSecret(), result.sessionId(), result.subscriptionId()));
    }
}
