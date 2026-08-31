package com.envisionad.webservice.advertisement.exceptions;

public class CampaignIsActiveCampaignException extends RuntimeException {
    public CampaignIsActiveCampaignException(String campaignId) {
        super("Campaign " + campaignId + " cannot be deleted because it is the business's active "
                + "campaign. Swap to another campaign first, then archive this one.");
    }
}
