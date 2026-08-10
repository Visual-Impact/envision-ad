package com.envisionad.webservice.payment.businesslogiclayer;

import com.envisionad.webservice.payment.presentationlayer.models.BundleSubscriptionResponseModel;
import com.envisionad.webservice.payment.presentationlayer.models.LiveCampaignResponseModel;
import com.stripe.exception.StripeException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

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

    /**
     * Every bundle subscription held by a business, newest first — the advertiser's own view of
     * what they are paying for (P1 M6).
     *
     * <p>Returns all statuses, not just live ones. INCOMPLETE matters because an abandoned
     * checkout occupies that business's one-live-subscription slot for the bundle until it is
     * cancelled, and the advertiser otherwise has no way to see or clear it; CANCELED matters as
     * history. Access is validated as employee-of-business, matching the rest of this service.
     */
    List<BundleSubscriptionResponseModel> getSubscriptionsForBusiness(Jwt jwt, String businessId);

    /**
     * The campaigns currently running on one screen, for the media owner's proof-of-display
     * picker (decision D40). Caller must be an employee of the business that owns the media.
     */
    List<LiveCampaignResponseModel> getLiveCampaignsForMedia(Jwt jwt, String mediaId);

    /**
     * Emails every affected media owner the advertiser's creatives, once, at the moment a
     * subscription first goes live. The legacy weekly-reservation flow used to send this on
     * every reservation; P1 M6 deleted that flow (and the email with it) without a
     * replacement. Called by the Stripe webhook handlers right after a subscription
     * transitions from INCOMPLETE to ACTIVE — never on a renewal or a PAST_DUE recovery.
     *
     * <p>Best-effort per owner: an owner whose email cannot be resolved, or whose send
     * fails, is logged and skipped rather than failing the caller — this must never roll
     * back subscription activation or a payout.
     */
    void notifyMediaOwnersOfNewSubscription(String subscriptionId);
}
