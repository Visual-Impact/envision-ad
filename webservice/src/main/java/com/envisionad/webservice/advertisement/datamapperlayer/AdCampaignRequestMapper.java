package com.envisionad.webservice.advertisement.datamapperlayer;

import com.envisionad.webservice.advertisement.dataaccesslayer.AdCampaign;
import com.envisionad.webservice.advertisement.presentationlayer.models.AdCampaignRequestModel;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;


@Mapper(componentModel = "spring")
public interface AdCampaignRequestMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "campaignId", ignore = true)
    @Mapping(target = "businessId", ignore = true)
    @Mapping(target = "ads", ignore = true)
    @Mapping(target = "archivedAt", ignore = true)
    @Mapping(target = "creativesUpdatedAt", ignore = true)
    AdCampaign requestModelToEntity(AdCampaignRequestModel adCampaign);

}
