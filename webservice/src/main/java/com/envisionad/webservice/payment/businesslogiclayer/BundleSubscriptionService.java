package com.envisionad.webservice.payment.businesslogiclayer;

import com.stripe.exception.StripeException;
import org.springframework.security.oauth2.jwt.Jwt;

public interface BundleSubscriptionService {

    /**
     * Starts a bundle subscription: validates the buyer, campaign and bundle, recomputes
     * the price server-side, ensures a Stripe Customer, opens a subscription-mode
     * embedded Checkout Session, and freezes the locked amount plus the per-owner split.
     *
     * <p>The resulting {@code bundle_subscriptions} row is INCOMPLETE until the
     * {@code checkout.session.completed} webhook links the Stripe subscription (M5).
     */
    SubscriptionCheckoutResult createSubscriptionCheckout(
            Jwt jwt, String bundleId, String campaignId, String businessId) throws StripeException;
}
