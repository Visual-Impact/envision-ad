package com.envisionad.webservice.payment.exceptions;

/**
 * Thrown when an advertiser retries a checkout they have in fact already paid for,
 * but whose activating webhook has not landed yet.
 *
 * <p>Without this guard the retry would open a second Checkout Session and charge
 * them a second time for the same bundle, leaving an orphaned Stripe subscription
 * billing monthly that the platform has no record of. Registered as 409.
 */
public class BundleSubscriptionAlreadyPaidException extends RuntimeException {

    public BundleSubscriptionAlreadyPaidException(String bundleId) {
        super("This bundle subscription has already been paid for and is being activated. "
                + "Please refresh in a moment rather than subscribing again (bundle " + bundleId + ").");
    }
}
