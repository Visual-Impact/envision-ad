package com.envisionad.webservice.payment.exceptions;

/**
 * Thrown when the charge behind a paid invoice cannot be looked up, before any media
 * owner has been paid for it.
 *
 * <p>Deliberately NOT registered in {@code GlobalControllerHandler}, for the same reason
 * as {@link BundleSubscriptionNotLinkedException}: {@code WebhookController} turns it into
 * a 500, which makes Stripe redeliver {@code invoice.paid}. No money has moved at this
 * point, so redelivery is safe, and it is better than guessing the funding source: a
 * transfer from the platform's available balance usually fails in live mode, where the
 * invoice's own payment is still pending.
 */
public class PayoutFundingLookupException extends RuntimeException {

    public PayoutFundingLookupException(String invoiceId, Throwable cause) {
        super("Could not look up the charge for invoice " + invoiceId
                + " to fund its media owner payouts. Returning 500 so Stripe redelivers this event.", cause);
    }
}
