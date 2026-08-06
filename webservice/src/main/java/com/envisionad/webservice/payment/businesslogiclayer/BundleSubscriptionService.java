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

    /**
     * Cancels a subscription at the end of the current billing period — never
     * immediately. The advertiser keeps the bundle's screens until
     * {@code current_period_end}, there is no refund, and the media owners keep what
     * they were already paid for the cycle. Stripe fires
     * {@code customer.subscription.deleted} at period end, which flips the local
     * status to CANCELED.
     *
     * <p>Idempotent: cancelling an already-cancelled subscription succeeds quietly
     * rather than erroring.
     */
    void cancelSubscription(Jwt jwt, String subscriptionId) throws StripeException;
}
