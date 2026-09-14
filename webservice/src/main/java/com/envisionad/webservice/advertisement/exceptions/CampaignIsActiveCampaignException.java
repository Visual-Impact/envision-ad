package com.envisionad.webservice.advertisement.exceptions;

public class CampaignIsActiveCampaignException extends RuntimeException {
    public CampaignIsActiveCampaignException(String campaignId) {
        super("Campaign " + campaignId + " is the business's active campaign, so it cannot be deleted "
                + "or archived. Swap to another campaign first.");
    }
}
