package com.envisionad.webservice.proofofdisplay.exceptions;

/**
 * Thrown when a media owner tries to submit proof of display for a campaign that is not
 * actually running on that screen.
 * <p>
 * P1 M6, decision D40 — replaces the weekly-reservation gate
 * ({@code existsConfirmedReservationForMediaAndCampaign}) that was removed with the
 * reservation system. The bundle-era question is "does this campaign hold a live bundle
 * subscription whose locked item set includes this media?", answered from
 * {@code bundle_subscription_items} joined to {@code bundle_subscriptions}.
 * <p>
 * Note the deliberate semantic shift: reservations gated on {@code CONFIRMED, PENDING};
 * this gates on {@code ACTIVE, PAST_DUE}, so an advertiser whose payment has failed but
 * whose subscription has not yet been cancelled still gets proof of display for the
 * cycle they are in.
 * <p>
 * Registered in {@code GlobalControllerHandler} as a 409 — without that it becomes a 500
 * via the catch-all (decision D9).
 */
public class MediaNotInActiveSubscriptionException extends RuntimeException {
    public MediaNotInActiveSubscriptionException(String mediaId, String campaignId) {
        super("Media " + mediaId + " is not part of a live bundle subscription for campaign " + campaignId);
    }
}
