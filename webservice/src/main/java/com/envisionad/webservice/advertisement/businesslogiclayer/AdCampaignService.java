package com.envisionad.webservice.advertisement.businesslogiclayer;

import com.envisionad.webservice.advertisement.presentationlayer.models.AdCampaignRequestModel;
import com.envisionad.webservice.advertisement.presentationlayer.models.AdCampaignResponseModel;
import com.envisionad.webservice.advertisement.presentationlayer.models.AdRequestModel;
import com.envisionad.webservice.advertisement.presentationlayer.models.AdResponseModel;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

public interface AdCampaignService {

    AdResponseModel addAdToCampaign(String campaignId, AdRequestModel adRequestModel);

    AdResponseModel deleteAdFromCampaign(String campaignId, String adId);

    AdResponseModel updateAdVenueTags(Jwt jwt, String businessId, String campaignId, String adId,
                                      List<String> venueIds);

    List<AdCampaignResponseModel> getAllAdCampaignsByBusinessId(String businessId);

    AdCampaignResponseModel getAdCampaignByCampaignId(String campaignId);

    AdCampaignResponseModel createAdCampaign(Jwt jwt, String businessId, AdCampaignRequestModel adCampaignRequestModel);

    List<String> getAllCampaignImageLinks(String campaignId);

    Integer getActiveCampaignCount(String businessId);

    AdCampaignResponseModel deleteAdCampaign(Jwt jwt, String businessId, String campaignId);
}
