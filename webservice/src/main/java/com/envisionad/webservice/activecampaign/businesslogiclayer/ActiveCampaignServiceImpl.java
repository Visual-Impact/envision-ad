package com.envisionad.webservice.activecampaign.businesslogiclayer;

import com.envisionad.webservice.activecampaign.dataaccesslayer.CampaignSwapEvent;
import com.envisionad.webservice.activecampaign.dataaccesslayer.CampaignSwapEventRepository;
import com.envisionad.webservice.activecampaign.dataaccesslayer.CampaignSwapEventType;
import com.envisionad.webservice.activecampaign.exceptions.ActiveCampaignAlreadySetException;
import com.envisionad.webservice.activecampaign.exceptions.SwapDebounceException;
import com.envisionad.webservice.activecampaign.presentationlayer.models.ActiveCampaignSummaryModel;
import com.envisionad.webservice.activecampaign.presentationlayer.models.NotificationResultModel;
import com.envisionad.webservice.advertisement.dataaccesslayer.Ad;
import com.envisionad.webservice.advertisement.dataaccesslayer.AdCampaign;
import com.envisionad.webservice.advertisement.dataaccesslayer.AdCampaignRepository;
import com.envisionad.webservice.advertisement.datamapperlayer.AdCampaignResponseMapper;
import com.envisionad.webservice.advertisement.datamapperlayer.AdResponseMapper;
import com.envisionad.webservice.advertisement.exceptions.AdCampaignNotFoundException;
import com.envisionad.webservice.advertisement.exceptions.CampaignHasNoAdsException;
import com.envisionad.webservice.advertisement.presentationlayer.models.AdCampaignResponseModel;
import com.envisionad.webservice.business.dataaccesslayer.Business;
import com.envisionad.webservice.business.dataaccesslayer.BusinessRepository;
import com.envisionad.webservice.business.exceptions.BusinessNotFoundException;
import com.envisionad.webservice.media.DataAccessLayer.Media;
import com.envisionad.webservice.media.DataAccessLayer.MediaRepository;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionItemRepository;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionRepository;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionStatus;
import com.envisionad.webservice.utils.JwtUtils;
import com.envisionad.webservice.utils.MediaOwnerNotifier;
import com.envisionad.webservice.venue.dataaccesslayer.Venue;
import com.envisionad.webservice.venue.dataaccesslayer.VenueRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ActiveCampaignServiceImpl implements ActiveCampaignService {

    /** The debounce reads only what a person triggered — the sweep is exempt (FR-7.4). */
    private static final List<CampaignSwapEventType> HUMAN_NOTIFY_EVENTS =
            List.of(CampaignSwapEventType.SWAP, CampaignSwapEventType.MANUAL_NOTIFY);

    private final BusinessRepository businessRepository;
    private final AdCampaignRepository adCampaignRepository;
    private final CampaignSwapEventRepository campaignSwapEventRepository;
    private final BundleSubscriptionRepository bundleSubscriptionRepository;
    private final BundleSubscriptionItemRepository bundleSubscriptionItemRepository;
    private final MediaRepository mediaRepository;
    private final VenueRepository venueRepository;
    private final AdCampaignResponseMapper adCampaignResponseMapper;
    private final AdResponseMapper adResponseMapper;
    private final CreativeDistributor creativeDistributor;
    private final MediaOwnerNotifier mediaOwnerNotifier;
    private final JwtUtils jwtUtils;

    /**
     * How long after a person-triggered notification the next one is refused. Configurable
     * rather than inlined so the window can be tuned once there is real usage data, without a
     * redeploy of changed code.
     */
    private final Duration swapDebounce;

    public ActiveCampaignServiceImpl(
            BusinessRepository businessRepository,
            AdCampaignRepository adCampaignRepository,
            CampaignSwapEventRepository campaignSwapEventRepository,
            BundleSubscriptionRepository bundleSubscriptionRepository,
            BundleSubscriptionItemRepository bundleSubscriptionItemRepository,
            MediaRepository mediaRepository,
            VenueRepository venueRepository,
            AdCampaignResponseMapper adCampaignResponseMapper,
            AdResponseMapper adResponseMapper,
            CreativeDistributor creativeDistributor,
            MediaOwnerNotifier mediaOwnerNotifier,
            JwtUtils jwtUtils,
            @Value("${envision.active-campaign.swap-debounce}") Duration swapDebounce) {
        this.businessRepository = businessRepository;
        this.adCampaignRepository = adCampaignRepository;
        this.campaignSwapEventRepository = campaignSwapEventRepository;
        this.bundleSubscriptionRepository = bundleSubscriptionRepository;
        this.bundleSubscriptionItemRepository = bundleSubscriptionItemRepository;
        this.mediaRepository = mediaRepository;
        this.venueRepository = venueRepository;
        this.adCampaignResponseMapper = adCampaignResponseMapper;
        this.adResponseMapper = adResponseMapper;
        this.creativeDistributor = creativeDistributor;
        this.mediaOwnerNotifier = mediaOwnerNotifier;
        this.jwtUtils = jwtUtils;
        this.swapDebounce = swapDebounce;
    }

    @Transactional(readOnly = true)
    @Override
    public ActiveCampaignSummaryModel getActiveCampaignSummary(String businessId) {
        Business business = requireBusiness(businessId);
        if (business.getActiveCampaignId() == null) {
            return null;
        }
        AdCampaign campaign = adCampaignRepository.findByCampaignIdWithAds(business.getActiveCampaignId());
        if (campaign == null) {
            // The pointer survives campaign deletion only as ON DELETE SET NULL, so a non-null
            // pointer to a missing campaign means the data is inconsistent, not merely empty.
            log.error("Business {} points at active campaign {}, which does not exist",
                    businessId, business.getActiveCampaignId());
            throw new AdCampaignNotFoundException(business.getActiveCampaignId());
        }
        return summaryOf(business, campaign);
    }

    @Transactional
    @Override
    public ActiveCampaignSummaryModel selectInitialActiveCampaign(String businessId, String campaignId) {
        Business business = requireBusiness(businessId);
        if (business.getActiveCampaignId() != null) {
            throw new ActiveCampaignAlreadySetException(businessId);
        }

        AdCampaign campaign = requireOwnedCampaignWithAds(businessId, campaignId);
        business.setActiveCampaignId(campaignId);
        businessRepository.save(business);

        // No email: at the moment of a first subscription the screens are only just becoming
        // subscribed, so no owner has anything to change. The row is written anyway, because the
        // audit trail of what went on screen should not start halfway through.
        recordEvent(businessId, null, campaignId, CampaignSwapEventType.INITIAL_SELECTION, null, 0, 0);
        return summaryOf(business, campaign);
    }

    @Transactional
    @Override
    public ActiveCampaignSummaryModel swapActiveCampaign(Jwt jwt, String businessId, String campaignId) {
        jwtUtils.validateUserIsEmployeeOfBusiness(jwt, businessId);
        Business business = requireBusiness(businessId);
        String previousCampaignId = business.getActiveCampaignId();

        AdCampaign campaign = requireOwnedCampaignWithAds(businessId, campaignId);

        if (campaignId.equals(previousCampaignId)) {
            // Defensive only — the UI disables the confirm button for this. Short-circuiting
            // here rather than throwing means a double-submit costs nothing: no duplicate email,
            // no event row, and crucially no cooldown consumed by a click that changed nothing.
            return summaryOf(business, campaign);
        }

        requireOutsideDebounce(businessId);

        business.setActiveCampaignId(campaignId);
        businessRepository.save(business);

        MediaOwnerNotifier.Outcome outcome = notifyOwnersOf(business, campaign, false);
        recordEvent(businessId, previousCampaignId, campaignId, CampaignSwapEventType.SWAP,
                jwtUtils.extractUserId(jwt), outcome.notified(), outcome.failed());

        return summaryOf(business, campaign);
    }

    @Transactional
    @Override
    public NotificationResultModel notifyMediaOwners(Jwt jwt, String businessId) {
        jwtUtils.validateUserIsEmployeeOfBusiness(jwt, businessId);
        Business business = requireBusiness(businessId);
        String activeCampaignId = business.getActiveCampaignId();
        if (activeCampaignId == null) {
            throw new AdCampaignNotFoundException("none set for business " + businessId);
        }

        AdCampaign campaign = requireOwnedCampaignWithAds(businessId, activeCampaignId);
        requireOutsideDebounce(businessId);

        MediaOwnerNotifier.Outcome outcome = notifyOwnersOf(business, campaign, true);
        recordEvent(businessId, activeCampaignId, activeCampaignId, CampaignSwapEventType.MANUAL_NOTIFY,
                jwtUtils.extractUserId(jwt), outcome.notified(), outcome.failed());

        return new NotificationResultModel(outcome.notified(), outcome.failed());
    }

    @Transactional(readOnly = true)
    @Override
    public List<AdCampaignResponseModel> getCampaignsEligibleForSwap(String businessId) {
        Business business = requireBusiness(businessId);
        String activeCampaignId = business.getActiveCampaignId();

        List<AdCampaign> eligible = adCampaignRepository.findAllByBusinessId_BusinessId(businessId).stream()
                .filter(campaign -> campaign.getAds() != null && !campaign.getAds().isEmpty())
                .filter(campaign -> !campaign.getCampaignId().getCampaignId().equals(activeCampaignId))
                .toList();
        return adCampaignResponseMapper.entitiesToResponseModelList(eligible);
    }

    /**
     * Every screen this advertiser is currently paying to appear on, minus the ones the P4
     * business-type rule excludes. The single seam brief 06 describes: everything about "who is
     * affected" is decided here and nowhere else.
     */
    List<Media> resolveAffectedMediaForBusiness(Business business) {
        List<UUID> mediaIds = bundleSubscriptionItemRepository.findDistinctMediaIdsByAdvertiserBusinessId(
                business.getBusinessId().getBusinessId(), BundleSubscriptionStatus.LIVE);
        if (mediaIds.isEmpty()) {
            return List.of();
        }

        List<Media> media = mediaRepository.findAllByIdWithLocation(mediaIds);

        // P4's competitive exclusion, in its FR-4.1 form: an advertiser's creatives never go up
        // in a venue of the advertiser's own business type — a gym does not advertise on a rival
        // gym's screen. P4 owns any wider definition of this; the single-field rule is all that
        // is implemented here.
        String excludedVenueId = business.getBusinessTypeVenueId();
        if (excludedVenueId == null || excludedVenueId.isBlank()) {
            return media;
        }
        return media.stream()
                .filter(screen -> !excludedVenueId.equals(screen.getVenueId()))
                .toList();
    }

    private MediaOwnerNotifier.Outcome notifyOwnersOf(Business business, AdCampaign campaign, boolean isUpdate) {
        List<Media> affectedMedia = resolveAffectedMediaForBusiness(business);
        if (affectedMedia.isEmpty()) {
            return new MediaOwnerNotifier.Outcome(0, 0);
        }

        List<OwnerCreativeGroup> groups = creativeDistributor.groupCreativesByOwnerAndVenue(
                campaign, affectedMedia, ownerResolver(), venueLabels());

        String subject = "New creatives for your Envision Ad screens — " + business.getName();
        List<MediaOwnerNotifier.OwnerMessage> messages = groups.stream()
                .map(group -> new MediaOwnerNotifier.OwnerMessage(
                        group.ownerBusinessId(), subject,
                        buildEmailBody(business.getName(), campaign, group, isUpdate)))
                .toList();

        return mediaOwnerNotifier.send(messages);
    }

    /**
     * Attributes a screen to the business that must act on the email. Uses the screen's current
     * owner rather than the owner frozen into the subscription at checkout: the person who has
     * to walk over and change what is displayed is whoever holds the screen today.
     */
    private Function<Media, String> ownerResolver() {
        return media -> media.getBusinessId() == null ? null : media.getBusinessId().toString();
    }

    private Map<String, String> venueLabels() {
        return venueRepository.findAll().stream()
                .collect(Collectors.toMap(Venue::getVenueId, Venue::getNameEn, (first, second) -> first));
    }

    /**
     * FR-4.6's plain-text body. {@code isUpdate} changes the framing, not the layout: an owner
     * who receives a swap-shaped email about a campaign they already have loaded would
     * reasonably read it as a duplicate and ignore it, leaving retired creative on the screen.
     */
    private String buildEmailBody(String advertiserName, AdCampaign campaign,
                                  OwnerCreativeGroup group, boolean isUpdate) {
        StringBuilder body = new StringBuilder("Hi there,\n\n");
        if (isUpdate) {
            body.append(advertiserName)
                    .append(" has changed the creatives for \"")
                    .append(campaign.getName())
                    .append("\", the campaign already running on your screens.\n")
                    .append("Please replace the previous set with the following:\n\n");
        } else {
            body.append(advertiserName)
                    .append(" has updated the campaign displayed on your screens to \"")
                    .append(campaign.getName())
                    .append("\".\n")
                    .append("Please update your displays with the following creatives:\n\n");
        }

        for (OwnerCreativeGroup.Section section : group.sections()) {
            if (section.venueLabel() != null) {
                body.append("For your ").append(section.venueLabel()).append(" screens:\n");
            }
            for (Ad ad : section.creatives()) {
                body.append("- ").append(ad.getName())
                        .append(" (").append(ad.getAdType()).append("): ")
                        .append(ad.getAdUrl()).append("\n");
            }
            body.append("\n");
        }

        body.append("Screens affected: ")
                .append(group.screens().stream().map(Media::getTitle).collect(Collectors.joining(", ")))
                .append("\n\nThanks,\n— The Envision Ad Team");
        return body.toString();
    }

    private void requireOutsideDebounce(String businessId) {
        campaignSwapEventRepository
                .findTopByBusinessIdAndEventTypeInOrderByTriggeredAtDesc(businessId, HUMAN_NOTIFY_EVENTS)
                .ifPresent(latest -> {
                    LocalDateTime availableAt = latest.getTriggeredAt().plus(swapDebounce);
                    LocalDateTime now = LocalDateTime.now();
                    if (now.isBefore(availableAt)) {
                        throw new SwapDebounceException(
                                (int) Math.max(1, Duration.between(now, availableAt).toSeconds()));
                    }
                });
    }

    private Business requireBusiness(String businessId) {
        Business business = businessRepository.findByBusinessId_BusinessId(businessId);
        if (business == null) {
            throw new BusinessNotFoundException(businessId);
        }
        return business;
    }

    /** Ownership and "has something to show" are the same two checks for select and swap alike. */
    private AdCampaign requireOwnedCampaignWithAds(String businessId, String campaignId) {
        AdCampaign campaign = adCampaignRepository.findByCampaignIdWithAds(campaignId);
        if (campaign == null || !campaign.getBusinessId().getBusinessId().equals(businessId)) {
            // Deliberately the same 404 either way: whether a campaign exists is not something
            // one business should be able to learn about another's.
            throw new AdCampaignNotFoundException(campaignId);
        }
        if (campaign.getAds() == null || campaign.getAds().isEmpty()) {
            throw new CampaignHasNoAdsException(campaignId);
        }
        return campaign;
    }

    private void recordEvent(String businessId, String fromCampaignId, String toCampaignId,
                             CampaignSwapEventType eventType, String triggeredByUserId,
                             int notified, int failed) {
        CampaignSwapEvent event = new CampaignSwapEvent();
        event.setBusinessId(businessId);
        event.setFromCampaignId(fromCampaignId);
        event.setToCampaignId(toCampaignId);
        event.setEventType(eventType);
        event.setTriggeredByUserId(triggeredByUserId);
        event.setRecipientsNotified(notified);
        event.setRecipientsFailed(failed);
        campaignSwapEventRepository.save(event);
    }

    private ActiveCampaignSummaryModel summaryOf(Business business, AdCampaign campaign) {
        String businessId = business.getBusinessId().getBusinessId();

        LocalDateTime lastSwapAt = campaignSwapEventRepository
                .findTopByBusinessIdAndEventTypeInOrderByTriggeredAtDesc(businessId, HUMAN_NOTIFY_EVENTS)
                .map(CampaignSwapEvent::getTriggeredAt)
                .orElse(null);
        LocalDateTime swapAvailableAt = null;
        if (lastSwapAt != null) {
            LocalDateTime candidate = lastSwapAt.plus(swapDebounce);
            // Reported only while it is actually in force; a window that has already passed is
            // not a countdown, and rendering one would disable a button that works.
            swapAvailableAt = candidate.isAfter(LocalDateTime.now()) ? candidate : null;
        }

        List<Media> affectedMedia = resolveAffectedMediaForBusiness(business);
        int bundleCount = (int) bundleSubscriptionRepository
                .findAllByAdvertiserBusinessIdAndStatusIn(businessId, BundleSubscriptionStatus.LIVE)
                .stream()
                .map(subscription -> subscription.getBundleId())
                .distinct()
                .count();

        ActiveCampaignSummaryModel summary = new ActiveCampaignSummaryModel();
        summary.setCampaignId(campaign.getCampaignId().getCampaignId());
        summary.setName(campaign.getName());
        summary.setAds(adResponseMapper.entitiesToResponseModelList(new ArrayList<>(campaign.getAds())));
        summary.setSubscribedBundleCount(bundleCount);
        summary.setSubscribedScreenCount(affectedMedia.size());
        summary.setLastSwapAt(lastSwapAt);
        summary.setSwapAvailableAt(swapAvailableAt);
        summary.setHasUnnotifiedCreativeChanges(hasUnnotifiedCreativeChanges(campaign));
        return summary;
    }

    /**
     * FR-8.2, derived rather than stored so it cannot drift out of step with reality: creatives
     * have changed since owners were last told about <em>this campaign</em>. Note the key and the
     * event set both differ from the debounce above — that asks "did this business notify anyone
     * recently", this asks "is this campaign's current content still what owners were sent",
     * which the automatic sweep also answers and therefore counts toward.
     *
     * <p>Only the stamping half is missing until M2b, so this reads false everywhere today; it is
     * wired now so the dashboard contract does not change under M3 later.
     */
    private boolean hasUnnotifiedCreativeChanges(AdCampaign campaign) {
        LocalDateTime creativesUpdatedAt = campaign.getCreativesUpdatedAt();
        if (creativesUpdatedAt == null) {
            return false;
        }
        return campaignSwapEventRepository
                .findTopByToCampaignIdAndEventTypeInOrderByTriggeredAtDesc(
                        campaign.getCampaignId().getCampaignId(),
                        List.of(CampaignSwapEventType.SWAP, CampaignSwapEventType.MANUAL_NOTIFY,
                                CampaignSwapEventType.AUTO_NOTIFY))
                .map(latest -> creativesUpdatedAt.isAfter(latest.getTriggeredAt()))
                .orElse(true);
    }
}
