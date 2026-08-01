package com.envisionad.webservice.payment.presentationlayer.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A campaign currently running on a given screen, for the media owner's proof-of-display
 * campaign picker (P1 M6, decision D40).
 * <p>
 * This replaces the frontend's old approach of fetching that screen's reservations and
 * deriving the distinct campaign list client-side. Filtering to live subscriptions now happens
 * server-side, so the picker cannot offer a campaign the proof endpoint would reject.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LiveCampaignResponseModel {
    private String campaignId;
    private String campaignName;
}
