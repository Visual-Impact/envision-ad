package com.envisionad.webservice.advertisement.datamapperlayer;

import com.envisionad.webservice.advertisement.dataaccesslayer.AdCampaign;
import com.envisionad.webservice.advertisement.presentationlayer.models.AdCampaignResponseModel;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

@Mapper(componentModel = "spring", uses = {AdResponseMapper.class})
public interface AdCampaignResponseMapper {

    @Mapping(target = "campaignId", source = "campaignId.campaignId")
    AdCampaignResponseModel entityToResponseModel(AdCampaign adCampaign);

    List<AdCampaignResponseModel> entitiesToResponseModelList(List<AdCampaign> adCampaigns);

    /**
     * Stored times carry no zone and are written in the JVM's zone, which is UTC in production.
     * Sent without an offset, a browser would read them as its own local time (P6 D23).
     */
    default OffsetDateTime withOffset(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
