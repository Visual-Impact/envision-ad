package com.envisionad.webservice.payment.exceptions;

/**
 * Thrown when {@code invoice.paid} arrives for a subscription no local row can be
 * resolved to.
 *
 * <p>Deliberately NOT registered in {@code GlobalControllerHandler}. Every other
 * domain exception is registered so it returns a meaningful status instead of the
 * catch-all's 500 (decision D9) — here the 500 <em>is</em> the meaningful outcome:
 * {@code WebhookController} catches it and returns 500, which is how Stripe is told
 * to redeliver the event. Registering it would turn a recoverable ordering problem
 * into a silently dropped payout.
 *
 * <p>This is the last line of defence rather than the normal path. The handler first
 * resolves the row by our own {@code subscriptionId}, carried in the subscription's
 * Stripe metadata, which succeeds on the very first delivery.
 */
public class BundleSubscriptionNotLinkedException extends RuntimeException {

    public BundleSubscriptionNotLinkedException(String stripeSubscriptionId, String invoiceId) {
        super("No bundle subscription found for Stripe subscription " + stripeSubscriptionId
                + " (invoice " + invoiceId + "). Returning 500 so Stripe redelivers this event.");
    }
}
