package com.envisionad.webservice.payment.businesslogiclayer;

/**
 * What the subscribe path hands back to the controller: the embedded-checkout client
 * secret the browser mounts, plus the ids needed to correlate the attempt.
 */
public record SubscriptionCheckoutResult(
        String clientSecret,
        String sessionId,
        String subscriptionId) {
}
