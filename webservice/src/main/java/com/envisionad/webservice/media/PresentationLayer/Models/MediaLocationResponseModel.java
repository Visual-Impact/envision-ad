package com.envisionad.webservice.media.PresentationLayer.Models;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
public class MediaLocationResponseModel {
    private UUID id;
    private String name;
    private String country;
    private String province;
    private String city;
    private String region;
    private String street;
    private String postalCode;
    private Double latitude;
    private Double longitude;
    private UUID businessId;
    private List<MediaResponseModel> mediaList;
}