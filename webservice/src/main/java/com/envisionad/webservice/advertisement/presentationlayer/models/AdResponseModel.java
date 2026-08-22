package com.envisionad.webservice.advertisement.presentationlayer.models;


import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


@Data
@NoArgsConstructor
public class AdResponseModel {
    private String adId;
    private String campaignId;

    private String name;
    private String adUrl;
    private String adType;

    // Always an array, never null — the frontend types this as a required string[].
    private List<String> venueIds;
}
