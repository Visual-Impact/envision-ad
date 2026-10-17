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
     * <p>Throws {@code PayoutFundingLookupException} only when the invoice's charge
     * cannot be looked up, which happens before any transfer is made, so a
     * redelivery cannot pay anyone twice.
     *
     * @param stripeInvoiceId the Stripe invoice being paid out; the idempotency unit
     * @param subscriptionId  local {@code bundle_subscriptions.subscription_id}
     */
    void payOutInvoice(String stripeInvoiceId, String subscriptionId);

    /**
     * Retries payouts that Stripe rejected only because the platform's available balance
     * could not cover them ({@code balance_insufficient}), once funds have had time to
     * settle. Each retry repeats the original idempotency key and parameters, and first
     * checks Stripe for a transfer that already went through, so it cannot pay twice.
     * Never throws; one row's failure does not stop the others.
     */
    void retryInsufficientFundsPayouts();
}
