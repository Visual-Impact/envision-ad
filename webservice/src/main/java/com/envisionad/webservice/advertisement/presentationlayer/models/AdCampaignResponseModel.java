package com.envisionad.webservice.advertisement.presentationlayer.models;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@NoArgsConstructor
public class AdCampaignResponseModel {
    private String campaignId;

    private String name;
    private List<AdResponseModel> ads;
    /** Null while the campaign is in the advertiser's list; set once they archive it. */
    private OffsetDateTime archivedAt;
}
