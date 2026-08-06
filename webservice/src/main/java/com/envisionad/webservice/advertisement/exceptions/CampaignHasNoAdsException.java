package com.envisionad.webservice.advertisement.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * A campaign with no ads has nothing to display, so it cannot be the active campaign of
 * a bundle subscription (P1 brief req. 11 — the P6 active-campaign-slot hook).
 */
@ResponseStatus(code = HttpStatus.CONFLICT, reason = "Campaign has no ads")
public class CampaignHasNoAdsException extends RuntimeException {
    public CampaignHasNoAdsException(String campaignId) {
        super("Campaign " + campaignId + " has no ads and cannot be used for a subscription.");
    }
}
