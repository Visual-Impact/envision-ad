package com.envisionad.webservice.advertisement.datamapperlayer;

import com.envisionad.webservice.advertisement.dataaccesslayer.Ad;
import com.envisionad.webservice.advertisement.presentationlayer.models.AdRequestModel;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AdRequestMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "adIdentifier", ignore = true)
    @Mapping(target = "campaign", ignore = true)

    @Mapping(target = "adType", ignore = true)

    // Resolved from venueIds in the service layer so an unknown ID can throw
    // VenueNotFoundException before anything is persisted.
    @Mapping(target = "venues", ignore = true)

    Ad requestModelToEntity(AdRequestModel adRequestModel);

}
