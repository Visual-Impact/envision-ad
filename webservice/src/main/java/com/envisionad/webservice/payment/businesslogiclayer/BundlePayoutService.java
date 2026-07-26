package com.envisionad.webservice.payment.businesslogiclayer;

public interface BundlePayoutService {

    /**
     * Splits one paid invoice across the media owners of a bundle subscription,
     * creating one Stripe {@code Transfer} per distinct owner.
     *
     * <p>Idempotent per {@code (stripeInvoiceId, owner)}: safe to call again for a
     * redelivered {@code invoice.paid} event. Never throws for a single owner's
     * failure — one owner's missing Connect account or rejected transfer must not
     * stop the others being paid, and must not fail the webhook (which would make
     * Stripe redeliver an event that has already moved money).
     *
     * @param stripeInvoiceId the Stripe invoice being paid out; the idempotency unit
     * @param subscriptionId  local {@code bundle_subscriptions.subscription_id}
     */
    void payOutInvoice(String stripeInvoiceId, String subscriptionId);
}
