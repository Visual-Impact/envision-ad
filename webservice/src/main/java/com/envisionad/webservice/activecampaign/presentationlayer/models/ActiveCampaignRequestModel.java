package com.envisionad.webservice.activecampaign.presentationlayer.models;

import lombok.Data;
import lombok.NoArgsConstructor;

/** The body of both {@code /select} and the swap {@code PUT} — the campaign to put on screen. */
@Data
@NoArgsConstructor
public class ActiveCampaignRequestModel {
    private String campaignId;
}
