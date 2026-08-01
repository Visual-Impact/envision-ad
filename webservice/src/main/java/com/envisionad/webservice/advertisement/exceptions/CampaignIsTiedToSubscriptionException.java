package com.envisionad.webservice.advertisement.exceptions;

/**
 * Thrown when a campaign cannot be deleted because it is the active campaign of a live
 * bundle subscription (P1 M6, decision D42 — renamed from
 * {@code CampaignIsTiedToReservationException} when the weekly-reservation system was
 * retired).
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
        super("Operation not allowed because campaign is the active campaign of a live bundle subscription: "
                + campaignId);
    }
}
