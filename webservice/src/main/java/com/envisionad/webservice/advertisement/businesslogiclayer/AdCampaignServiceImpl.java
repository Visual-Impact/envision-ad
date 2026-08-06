package com.envisionad.webservice.advertisement.businesslogiclayer;

import com.cloudinary.Cloudinary;
import com.envisionad.webservice.advertisement.dataaccesslayer.*;
import com.envisionad.webservice.advertisement.datamapperlayer.AdCampaignRequestMapper;
import com.envisionad.webservice.advertisement.datamapperlayer.AdCampaignResponseMapper;
import com.envisionad.webservice.advertisement.datamapperlayer.AdRequestMapper;
import com.envisionad.webservice.advertisement.datamapperlayer.AdResponseMapper;
import com.envisionad.webservice.advertisement.exceptions.*;
import com.envisionad.webservice.advertisement.presentationlayer.models.AdCampaignRequestModel;
import com.envisionad.webservice.advertisement.presentationlayer.models.AdCampaignResponseModel;
import com.envisionad.webservice.advertisement.presentationlayer.models.AdRequestModel;
import com.envisionad.webservice.advertisement.presentationlayer.models.AdResponseModel;
import com.envisionad.webservice.business.dataaccesslayer.Business;
import com.envisionad.webservice.business.dataaccesslayer.BusinessIdentifier;
import com.envisionad.webservice.business.dataaccesslayer.BusinessRepository;
import com.envisionad.webservice.business.exceptions.BusinessNotFoundException;
import com.envisionad.webservice.utils.CloudinaryConfig;
import com.envisionad.webservice.utils.JwtUtils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionRepository;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionStatus;

@Slf4j
@Service
public class AdCampaignServiceImpl implements AdCampaignService {
    private final BusinessRepository businessRepository;
    private final AdCampaignRepository adCampaignRepository;
    private final AdCampaignRequestMapper adCampaignRequestMapper;
    private final AdCampaignResponseMapper adCampaignResponseMapper;
    private final AdRequestMapper adRequestMapper;
    private final AdResponseMapper adResponseMapper;
    private final JwtUtils jwtUtils;
    private final BundleSubscriptionRepository bundleSubscriptionRepository;
    private final Cloudinary cloudinary;

    /** The subscription states that make a campaign undeletable — mirrors the DB trigger. */
    private static final List<BundleSubscriptionStatus> LIVE_SUBSCRIPTION_STATUSES =
            List.of(BundleSubscriptionStatus.ACTIVE, BundleSubscriptionStatus.PAST_DUE);

    public AdCampaignServiceImpl(AdCampaignRepository adCampaignRepository, AdCampaignRequestMapper adCampaignRequestMapper, AdCampaignResponseMapper adCampaignResponseMapper, AdRequestMapper adRequestMapper, AdResponseMapper adResponseMapper, BusinessRepository businessRepository, JwtUtils jwtUtils, BundleSubscriptionRepository bundleSubscriptionRepository, Cloudinary cloudinary) {
        this.businessRepository = businessRepository;
        this.adCampaignRepository = adCampaignRepository;
        this.adCampaignRequestMapper = adCampaignRequestMapper;
        this.adCampaignResponseMapper = adCampaignResponseMapper;
        this.adRequestMapper = adRequestMapper;
        this.adResponseMapper = adResponseMapper;
        this.jwtUtils = jwtUtils;
        this.cloudinary = cloudinary;
        this.bundleSubscriptionRepository = bundleSubscriptionRepository;
    }

    @Override
    public List<AdCampaignResponseModel> getAllAdCampaignsByBusinessId(String businessId) {
        List<AdCampaign> adCampaigns = adCampaignRepository.findAllByBusinessId_BusinessId(businessId);
        return adCampaignResponseMapper.entitiesToResponseModelList(adCampaigns);
    }

    @Override
    public AdCampaignResponseModel getAdCampaignByCampaignId(String campaignId) {
        AdCampaign adCampaign = adCampaignRepository.findByCampaignId_CampaignId(campaignId);

        if (adCampaign == null){
            throw new AdCampaignNotFoundException(campaignId);
        }

        return adCampaignResponseMapper.entityToResponseModel(adCampaign);
    }

    @Override
    public AdCampaignResponseModel createAdCampaign(Jwt jwt, String businessId,
            AdCampaignRequestModel adCampaignRequestModel) {
        Business business = businessRepository.findByBusinessId_BusinessId(businessId);

        if (business == null) {
            throw new BusinessNotFoundException(businessId);
        }

        String userId = jwtUtils.extractUserId(jwt);
        jwtUtils.validateUserIsEmployeeOfBusiness(userId, businessId);

        AdCampaign adCampaign = adCampaignRequestMapper.requestModelToEntity(adCampaignRequestModel);
        adCampaign.setCampaignId(new AdCampaignIdentifier());
        adCampaign.setBusinessId(new BusinessIdentifier(businessId));

        return adCampaignResponseMapper.entityToResponseModel(adCampaignRepository.save(adCampaign));
    }

    @Override
    public List<String> getAllCampaignImageLinks(String campaignId) {
        AdCampaign adCampaign = adCampaignRepository.findByCampaignId_CampaignId(campaignId);
        if (adCampaign == null) {
            throw new AdCampaignNotFoundException(campaignId);
        }

        return adCampaign.getAds().stream()
                .map(Ad::getAdUrl)
                .filter(url -> url != null && !url.isEmpty())
                .toList();
    }

    @Override
    public AdResponseModel addAdToCampaign(String campaignId, AdRequestModel adRequestModel) {
        AdCampaign adCampaign = adCampaignRepository.findByCampaignId_CampaignId(campaignId);
        if (adCampaign == null)
            throw new AdCampaignNotFoundException(campaignId);

        // Deliberately unguarded (P1 M6, decision D42): an advertiser on a live monthly
        // subscription must be able to change their creative mid-cycle. The weekly-reservation
        // system blocked this because a booking was a short fixed window; a subscription is not.
        // Notifying the affected media owners of the change is P6's scope.
        Ad newAd = adRequestMapper.requestModelToEntity(adRequestModel);
        newAd.setAdIdentifier(new AdIdentifier());

        try {
            // valueOf throws IllegalArgumentException if the string doesn't match exactly
            AdType type = AdType.valueOf(adRequestModel.getAdType());
            newAd.setAdType(type);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidAdTypeException(adRequestModel.getAdType());
        }

        newAd.setCampaign(adCampaign);
        adCampaign.getAds().add(newAd);

        adCampaignRepository.save(adCampaign);
        return adResponseMapper.entityToResponseModel(newAd);
    }

    @Override
    public AdResponseModel deleteAdFromCampaign(String campaignId, String adId) {
        AdCampaign adCampaign = adCampaignRepository.findByCampaignId_CampaignId(campaignId);
        if (adCampaign == null) {
            throw new AdCampaignNotFoundException(campaignId);
        }

        // Deliberately unguarded — see addAdToCampaign above (decision D42).
        Ad adToDelete = adCampaign.getAds().stream()
                .filter(ad -> ad.getAdIdentifier().getAdIdentifier().equals(adId))
                .findFirst()
                .orElseThrow(() -> new AdNotFoundException(adId));

        deleteCloudinaryAssetIfPresent(adToDelete.getAdUrl());

        adCampaign.getAds().remove(adToDelete);
        adCampaignRepository.save(adCampaign);

        return adResponseMapper.entityToResponseModel(adToDelete);
    }

    @Override
    public AdCampaignResponseModel deleteAdCampaign(Jwt jwt, String businessId, String campaignId) {
        // Validate the user is an employee of the business

        if (businessId == null || businessId.isBlank()) {
            throw new BusinessNotFoundException(businessId);
        }

        if (campaignId == null || campaignId.isBlank()) {
            throw new AdCampaignNotFoundException(campaignId);
        }

        jwtUtils.validateUserIsEmployeeOfBusiness(jwt, businessId);

        AdCampaign adCampaign = adCampaignRepository.findByCampaignId_CampaignId(campaignId);
        if (adCampaign == null) {
            throw new AdCampaignNotFoundException(campaignId);
        }
        // Validate the campaign belongs to the business
        jwtUtils.validateBusinessOwnsCampaign(businessId, adCampaign);

        // Deletion IS still guarded (decision D42). This is not a product choice: the campaign is
        // referenced by bundle_subscriptions.campaign_id (NOT NULL, ON DELETE RESTRICT) and by the
        // prevent_active_campaign_delete() trigger, so the delete would fail at the database
        // anyway — the app-layer check is what turns that into a clean 409 instead of a
        // constraint violation surfacing as the catch-all's 500.
        if (campaignIsTiedToSubscription(campaignId)) {
            throw new CampaignIsTiedToSubscriptionException(campaignId);
        }

        // Delete all associated ads and their Cloudinary assets
        for (Ad ad : adCampaign.getAds()) {
            deleteCloudinaryAssetIfPresent(ad.getAdUrl());
        }

        adCampaignRepository.delete(adCampaign);
        return adCampaignResponseMapper.entityToResponseModel(adCampaign);
    }

    private void deleteCloudinaryAssetIfPresent(String url) {
        if (url == null) return;

        url = url.trim();
        if (url.isBlank()) return;

        try {
            String publicId = CloudinaryConfig.getPublicIdFromUrl(url);
            if (publicId == null || publicId.isBlank()) return;

            String resourceType = CloudinaryConfig.getResourceTypeFromUrl(url);

            Map<String, Object> options = new HashMap<>();
            options.put("invalidate", true);
            options.put("resource_type", resourceType);

            cloudinary.uploader().destroy(publicId, options);

        } catch (Exception e) {
            log.warn("Failed to delete Cloudinary asset for url={}", url, e);
        }
    }


    /**
     * Number of distinct campaigns this advertiser currently has running. Re-sourced in M6 from
     * reservations to bundle subscriptions (brief req. 20 — keep the metric definition, swap the
     * source). "Running" was CONFIRMED-and-within-its-date-range; a subscription has no date range,
     * so its equivalent is simply being live: ACTIVE or PAST_DUE.
     */
    @Override
    public Integer getActiveCampaignCount(String businessId) {
        return bundleSubscriptionRepository.countDistinctCampaignsByAdvertiserBusinessIdAndStatusIn(
                businessId, LIVE_SUBSCRIPTION_STATUSES);
    }

    /**
     * Matches the foreign key, not the trigger (D47). {@code bundle_subscriptions.campaign_id} is
     * {@code ON DELETE RESTRICT}, so <em>any</em> subscription row pins the campaign — a cancelled
     * one just as firmly as a live one. Checking only live statuses would let the delete through
     * the service and fail at the database with a generic message.
     */
    private boolean campaignIsTiedToSubscription(String campaignId) {
        return bundleSubscriptionRepository.existsByCampaignId(campaignId);
    }
}
