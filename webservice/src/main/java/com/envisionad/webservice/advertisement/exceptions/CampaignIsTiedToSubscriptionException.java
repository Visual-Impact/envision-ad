package com.envisionad.webservice.advertisement.exceptions;

/**
 * Thrown when a campaign cannot be deleted because a bundle subscription references it
 * (P1 M6, decision D42 — renamed from {@code CampaignIsTiedToReservationException} when the
 * weekly-reservation system was retired).
 * <p>
 * <strong>Any</strong> subscription blocks the delete, not just a live one (D47). The campaign
 * is the subscription's record of what actually ran on those screens, so
 * {@code bundle_subscriptions.campaign_id} is {@code ON DELETE RESTRICT} and the database
 * refuses regardless of status; this exception exists so the advertiser gets that explanation
 * rather than a generic constraint error. Cancelling a subscription therefore does not release
 * its campaign — an archive/hide action is the intended answer, and belongs to P6.
 * <p>
 * Only the delete path raises this. Adding and removing ads on a subscribed campaign is
 * deliberately allowed: an advertiser paying monthly must be able to change their creative
 * mid-cycle, and the swap UX that surrounds that is P6's scope.
 * <p>
 * Registered in {@code GlobalControllerHandler} as a 409 — without that registration the
 * catch-all {@code @ExceptionHandler(Exception.class)} would turn it into a 500 (decision D9).
 */
public class CampaignIsTiedToSubscriptionException extends RuntimeException {
    public CampaignIsTiedToSubscriptionException(String campaignId) {
        super("Campaign " + campaignId + " cannot be deleted: a bundle subscription references it. "
                + "Cancelling the subscription does not release the campaign — its billing history "
                + "keeps pointing at it.");
    }
}
