package com.envisionad.webservice.payment.exceptions;

/**
 * Registered in {@code GlobalControllerHandler} as 404 — without that it would hit
 * the catch-all and return 500 (decision D9).
 */
public class BundleSubscriptionNotFoundException extends RuntimeException {

    public BundleSubscriptionNotFoundException(String subscriptionId) {
        super("Bundle subscription not found: " + subscriptionId);
    }
}
