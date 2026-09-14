package com.envisionad.webservice.advertisement.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * An archived campaign cannot be put on screen (P6 FR-3b.4): not by a swap, a first pick or a
 * checkout. The advertiser unarchives it first. Keeping it off screen is also what keeps the
 * active campaign from ever being an archived one, which the dashboard relies on.
 */
@ResponseStatus(code = HttpStatus.CONFLICT, reason = "Campaign is archived")
public class CampaignIsArchivedException extends RuntimeException {
    public CampaignIsArchivedException(String campaignId) {
        super("Campaign " + campaignId + " is archived. Unarchive it before putting it on screen.");
    }
}
