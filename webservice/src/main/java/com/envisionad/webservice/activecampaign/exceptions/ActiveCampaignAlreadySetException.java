package com.envisionad.webservice.activecampaign.exceptions;

/**
 * Thrown by {@code POST /active-campaign/select} when the business already has an active
 * campaign. Selection is the first mandatory pick — it sends no email and is not rate-limited —
 * so it must not double as a way to change what is on screen; that is what the swap endpoint is
 * for, and the swap path is where the notification and cooldown rules live.
 */
public class ActiveCampaignAlreadySetException extends RuntimeException {
    public ActiveCampaignAlreadySetException(String businessId) {
        super("Business with businessId " + businessId + " already has an active campaign. "
                + "Use the swap endpoint to change which campaign is displayed.");
    }
}
