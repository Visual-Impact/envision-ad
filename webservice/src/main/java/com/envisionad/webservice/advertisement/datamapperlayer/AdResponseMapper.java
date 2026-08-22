package com.envisionad.webservice.advertisement.datamapperlayer;

import com.envisionad.webservice.advertisement.dataaccesslayer.Ad;
import com.envisionad.webservice.advertisement.presentationlayer.models.AdResponseModel;
import com.envisionad.webservice.venue.dataaccesslayer.Venue;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AdResponseMapper {

    @Mapping(target = "adId", source = "adIdentifier.adIdentifier")
    @Mapping(target = "campaignId", source = "campaign.campaignId.campaignId")
    // Explicit because the names differ — MapStruct will not auto-wire venues -> venueIds.
    @Mapping(target = "venueIds", source = "venues")
    AdResponseModel entityToResponseModel(Ad ad);

    List<AdResponseModel> entitiesToResponseModelList(List<Ad> ads);

    // Untagged ads must serialize as [], not null — the frontend types venueIds as required.
    default List<String> venuesToVenueIds(List<Venue> venues) {
        return venues == null ? List.of() : venues.stream().map(Venue::getVenueId).toList();
    }
}