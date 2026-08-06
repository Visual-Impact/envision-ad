package com.envisionad.webservice.payment.businesslogiclayer;

import com.stripe.exception.StripeException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Map;

public interface StripeService {
    String createConnectedAccount(Jwt jwt, String businessId);

    String createAccountLink(String stripeAccountId, String returnUrl, String refreshUrl) throws StripeException;

    /**
     * Create connected account and return onboarding url and account id in a map
     */
    Map<String, String> createConnectedAccountAndLink(Jwt jwt, String businessId, String returnUrl, String refreshUrl)
            throws StripeException;

    // createCheckoutSession / createAuthorizedCheckoutSession were removed in P1 M6 (brief
    // req. 18) along with the weekly-reservation flow they served. Bundle subscriptions use
    // BundleSubscriptionService, which creates its own subscription-mode session.

    /**
     * Get Stripe account status for a business
     */
    Map<String, Object> getAccountStatus(Jwt jwt, String businessId);

    Map<String, Object> getDashboardData(Jwt jwt, String businessId, String period) throws StripeException;

}
