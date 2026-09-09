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
import com.envisionad.webservice.venue.dataaccesslayer.Venue;
import com.envisionad.webservice.venue.dataaccesslayer.VenueRepository;
import com.envisionad.webservice.venue.exceptions.VenueNotFoundException;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
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
    private final VenueRepository venueRepository;

    /** The subscription states that make a campaign undeletable — mirrors the DB trigger. */
    private static final List<BundleSubscriptionStatus> LIVE_SUBSCRIPTION_STATUSES =
            List.of(BundleSubscriptionStatus.ACTIVE, BundleSubscriptionStatus.PAST_DUE);

    public AdCampaignServiceImpl(AdCampaignRepository adCampaignRepository, AdCampaignRequestMapper adCampaignRequestMapper, AdCampaignResponseMapper adCampaignResponseMapper, AdRequestMapper adRequestMapper, AdResponseMapper adResponseMapper, BusinessRepository businessRepository, JwtUtils jwtUtils, BundleSubscriptionRepository bundleSubscriptionRepository, Cloudinary cloudinary, VenueRepository venueRepository) {
        this.businessRepository = businessRepository;
        this.adCampaignRepository = adCampaignRepository;
        this.adCampaignRequestMapper = adCampaignRequestMapper;
        this.adCampaignResponseMapper = adCampaignResponseMapper;
        this.adRequestMapper = adRequestMapper;
        this.adResponseMapper = adResponseMapper;
        this.jwtUtils = jwtUtils;
        this.cloudinary = cloudinary;
        this.bundleSubscriptionRepository = bundleSubscriptionRepository;
        this.venueRepository = venueRepository;
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
    public AdResponseModel addAdToCampaign(Jwt jwt, String businessId, String campaignId,
                                           AdRequestModel adRequestModel) {
        jwtUtils.validateUserIsEmployeeOfBusiness(jwt, businessId);

        AdCampaign adCampaign = adCampaignRepository.findByCampaignId_CampaignId(campaignId);
        if (adCampaign == null)
            throw new AdCampaignNotFoundException(campaignId);

        jwtUtils.validateBusinessOwnsCampaign(businessId, adCampaign);

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

        if (newAd.getAdType() == AdType.VIDEO) {
            validateVideoDuration(newAd.getAdUrl());
        }

        // Resolved before any mutation so an unknown venue ID leaves nothing persisted.
        newAd.setVenues(new ArrayList<>(resolveVenues(adRequestModel.getVenueIds())));

        newAd.setCampaign(adCampaign);
        adCampaign.getAds().add(newAd);

        adCampaignRepository.save(adCampaign);
        return adResponseMapper.entityToResponseModel(newAd);
    }

    @Override
    public AdResponseModel deleteAdFromCampaign(Jwt jwt, String businessId, String campaignId, String adId) {
        jwtUtils.validateUserIsEmployeeOfBusiness(jwt, businessId);

        AdCampaign adCampaign = adCampaignRepository.findByCampaignId_CampaignId(campaignId);
        if (adCampaign == null) {
            throw new AdCampaignNotFoundException(campaignId);
        }

        jwtUtils.validateBusinessOwnsCampaign(businessId, adCampaign);

        // Deliberately unguarded — see addAdToCampaign above (decision D42).
        Ad adToDelete = adCampaign.getAds().stream()
                .filter(ad -> ad.getAdIdentifier().getAdIdentifier().equals(adId))
                .findFirst()
                .orElseThrow(() -> new AdNotFoundException(adId));

        // Map BEFORE removing: the response mapper reads the lazy venues collection, and
        // once the ad is removed and the campaign saved that would be a read on a deleted
        // entity. Harmless before P7 added the collection; a bug the moment it exists.
        AdResponseModel response = adResponseMapper.entityToResponseModel(adToDelete);

        // P6 FR-3.1a: the campaign currently on screen must keep at least one creative while
        // the advertiser is paying — an empty active campaign means live screens with nothing
        // to show. With no live subscription this does not apply; P1's resubscribe flow owns
        // the "active campaign must have >= 1 ad" check (FR-5.1 / FR-6).
        Business business = businessRepository.findByBusinessId_BusinessId(businessId);
        boolean deletingFinalCreative = adCampaign.getAds().size() == 1;
        boolean deletingFromActiveCampaign = business != null
                && campaignId.equals(business.getActiveCampaignId());

        if (deletingFinalCreative && deletingFromActiveCampaign && hasLiveSubscription(businessId)) {
            throw new LastActiveCampaignCreativeCannotBeDeletedException(campaignId);
        }

        deleteCloudinaryAssetIfPresent(adToDelete.getAdUrl());

        adCampaign.getAds().remove(adToDelete);
        adCampaignRepository.save(adCampaign);

        return response;
    }

    @Override
    public AdResponseModel updateAdVenueTags(Jwt jwt, String businessId, String campaignId, String adId,
                                             List<String> venueIds) {
        jwtUtils.validateUserIsEmployeeOfBusiness(jwt, businessId);

        AdCampaign adCampaign = adCampaignRepository.findByCampaignId_CampaignId(campaignId);
        if (adCampaign == null) {
            throw new AdCampaignNotFoundException(campaignId);
        }

        jwtUtils.validateBusinessOwnsCampaign(businessId, adCampaign);

        Ad ad = adCampaign.getAds().stream()
                .filter(a -> a.getAdIdentifier().getAdIdentifier().equals(adId))
                .findFirst()
                .orElseThrow(() -> new AdNotFoundException(adId));

        // Tag edits are metadata-only, so they are NOT blocked by the subscription-tie
        // check that gates campaign deletion.
        List<Venue> resolved = resolveVenues(venueIds);

        // Mutate the managed collection rather than replacing it — Hibernate tracks this
        // instance, and setVenues() on a managed entity detaches the tracked bag.
        ad.getVenues().clear();
        ad.getVenues().addAll(resolved);

        adCampaignRepository.save(adCampaign);

        return adResponseMapper.entityToResponseModel(ad);
    }

    /**
     * Resolves submitted venue IDs to entities, de-duplicating first. The de-dup is
     * required, not defensive: venues is mapped as a List (a bag), so a repeated ID would
     * be inserted twice and violate ad_venue_tags' composite primary key.
     *
     * <p>The whole set is resolved before the caller mutates anything, so an unresolvable
     * ID throws without leaving a partial tag set behind.
     */
    private List<Venue> resolveVenues(List<String> venueIds) {
        if (venueIds == null || venueIds.isEmpty()) {
            return List.of();
        }

        return new LinkedHashSet<>(venueIds).stream()
                .map(venueId -> venueRepository.findByVenueId(venueId)
                        .orElseThrow(() -> new VenueNotFoundException(venueId)))
                .toList();
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

        Business business = businessRepository.findByBusinessId_BusinessId(businessId);
        if (business == null) {
            throw new BusinessNotFoundException(businessId);
        }

        AdCampaign adCampaign = adCampaignRepository.findByCampaignId_CampaignId(campaignId);
        if (adCampaign == null) {
            throw new AdCampaignNotFoundException(campaignId);
        }
        // Validate the campaign belongs to the business
        jwtUtils.validateBusinessOwnsCampaign(businessId, adCampaign);

        // The active campaign is never deletable (P6 FR-3.1). To remove it from the list the
        // advertiser swaps to another campaign first and then archives this one — archiving
        // keeps the row, so no reference is ever left dangling. Unconditional on purpose: a
        // dangling active_campaign_id is a state we never want to produce, even for an
        // advertiser with no live subscription. Deletion is not the tool for a subscribed
        // or once-subscribed campaign; archive is.
        if (campaignId.equals(business.getActiveCampaignId())) {
            throw new CampaignIsActiveCampaignException(campaignId);
        }

        // The old status-agnostic "any bundle_subscriptions row references this campaign" guard
        // (D42/D47) is gone with the P6 follow-up: bundle_subscriptions.campaign_id and its
        // ON DELETE RESTRICT FK were dropped, so the only DB reference that blocks a delete is
        // business.active_campaign_id — already checked above (unconditionally, and a campaign
        // can only be its own owner's active campaign). Consequence, accepted by the lead: a
        // campaign that only ever ran on now-cancelled subscriptions is hard-deletable again.
        // No orphan risk — proof-of-display persists nothing, ads cascade, swap-event history
        // has its own FK actions.

        // Delete all associated ads and their Cloudinary assets
        for (Ad ad : adCampaign.getAds()) {
            deleteCloudinaryAssetIfPresent(ad.getAdUrl());
        }

        adCampaignRepository.delete(adCampaign);
        return adCampaignResponseMapper.entityToResponseModel(adCampaign);
    }

    /** Mirrors the client-side cap in AddAdModal.tsx — this is the defense against a direct API
     * call bypassing that check, not the primary UX path. */
    private static final int MAX_VIDEO_DURATION_SECONDS = 30;

    /**
     * Re-derives the video's real duration from Cloudinary rather than trusting a client-supplied
     * value, since the upload payload only carries the resulting URL. Fails open (logs and lets
     * the ad through) on a lookup error — this is a business-rule guard, not a security boundary,
     * and a Cloudinary Admin API hiccup shouldn't block ad creation.
     */
    private void validateVideoDuration(String adUrl) {
        String publicId = CloudinaryConfig.getPublicIdFromUrl(adUrl);
        if (publicId == null || publicId.isBlank()) return;

        try {
            Map<String, Object> options = new HashMap<>();
            options.put("resource_type", "video");
            Map<?, ?> resource = cloudinary.api().resource(publicId, options);

            Object durationObj = resource.get("duration");
            if (durationObj instanceof Number durationNumber) {
                double duration = durationNumber.doubleValue();
                if (duration > MAX_VIDEO_DURATION_SECONDS) {
                    throw new VideoTooLongException(duration, MAX_VIDEO_DURATION_SECONDS);
                }
            }
        } catch (VideoTooLongException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to verify video duration via Cloudinary for url={}", adUrl, e);
        }
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
     * Number of creatives (ads) in the advertiser's active campaign — the "what's actually on
     * screen right now" number for the advertiser-overview tile.
     * <p>
     * Redefined by the P6 follow-up: the old metric counted distinct campaigns across live
     * subscriptions, which collapses to 0-or-1 once there is one active campaign per business.
     * <p>
     * Gated on a live (ACTIVE/PAST_DUE) subscription exactly as the old metric was:
     * {@code business.active_campaign_id} is "sticky" and stays set after subscriptions lapse, so
     * a raw creative count would show "5 on screen" when nothing is subscribed. Returns 0 when the
     * pointer is null or no subscription is live.
     */
    @Override
    public Integer getActiveCampaignCreativeCount(String businessId) {
        Business business = businessRepository.findByBusinessId_BusinessId(businessId);
        if (business == null || business.getActiveCampaignId() == null) {
            return 0;
        }
        if (!hasLiveSubscription(businessId)) {
            return 0;
        }
        AdCampaign activeCampaign =
                adCampaignRepository.findByCampaignIdWithAds(business.getActiveCampaignId());
        return activeCampaign == null ? 0 : activeCampaign.getAds().size();
    }

    private boolean hasLiveSubscription(String businessId) {
        return bundleSubscriptionRepository.countByAdvertiserBusinessIdAndStatusIn(
                businessId, LIVE_SUBSCRIPTION_STATUSES) > 0;
    }
}
