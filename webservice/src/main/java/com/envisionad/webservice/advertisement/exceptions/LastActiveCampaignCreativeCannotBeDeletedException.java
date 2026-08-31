package com.envisionad.webservice.advertisement.exceptions;

public class LastActiveCampaignCreativeCannotBeDeletedException extends RuntimeException {
    public LastActiveCampaignCreativeCannotBeDeletedException(String campaignId) {
        super("The final creative in active campaign " + campaignId
                + " cannot be deleted while the advertiser has a live subscription.");
    }
}
