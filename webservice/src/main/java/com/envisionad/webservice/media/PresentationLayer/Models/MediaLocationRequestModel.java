package com.envisionad.webservice.media.PresentationLayer.Models;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MediaLocationRequestModel {
    private String name;
    private String country;
    private String province;
    private String city;
    private String region;
    private String street;
    private String postalCode;
    private Double latitude;
    private Double longitude;
    private String businessId;
    /** True when the media owner placed a pin manually instead of picking an autocomplete suggestion. */
    private Boolean manualCoordinates;
}
