package com.envisionad.webservice.proofofdisplay.businesslogiclayer;

import com.envisionad.webservice.advertisement.dataaccesslayer.AdCampaign;
import com.envisionad.webservice.advertisement.dataaccesslayer.AdCampaignRepository;
import com.envisionad.webservice.bundle.dataaccesslayer.Bundle;
import com.envisionad.webservice.bundle.dataaccesslayer.BundleRepository;
import com.envisionad.webservice.business.dataaccesslayer.Business;
import com.envisionad.webservice.business.dataaccesslayer.BusinessRepository;
import com.envisionad.webservice.business.dataaccesslayer.Employee;
import com.envisionad.webservice.business.dataaccesslayer.EmployeeRepository;
import com.envisionad.webservice.media.DataAccessLayer.Media;
import com.envisionad.webservice.media.DataAccessLayer.MediaRepository;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscription;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionItem;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionItemRepository;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionRepository;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionStatus;
import com.envisionad.webservice.payment.exceptions.BundleSubscriptionNotFoundException;
import com.envisionad.webservice.proofofdisplay.dataaccesslayer.ProofOfDisplayLink;
import com.envisionad.webservice.proofofdisplay.dataaccesslayer.ProofOfDisplayLinkRepository;
import com.envisionad.webservice.proofofdisplay.exceptions.InvalidProofLinkUrlException;
import com.envisionad.webservice.proofofdisplay.exceptions.ProofLinkMultiOwnerException;
import com.envisionad.webservice.proofofdisplay.exceptions.ProofLinkNotEditableException;
import com.envisionad.webservice.proofofdisplay.presentationlayer.models.MediaOwnerProofSubscriptionResponseModel;
import com.envisionad.webservice.proofofdisplay.presentationlayer.models.ProofLinkResponseModel;
import com.envisionad.webservice.proofofdisplay.presentationlayer.models.ProofLinkStatus;
import com.envisionad.webservice.proofofdisplay.presentationlayer.models.ProofOfDisplayLinkResponseModel;
import com.envisionad.webservice.utils.JwtUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Proof of display as a link (P12): a media owner points a subscription at a page they maintain
 * (typically Canva) and edits it in place; the advertiser reads it from their Subscriptions page.
 * Replaces the photo-email flow, which persisted nothing.
 */
@Service
public class ProofOfDisplayLinkService {

    /** Longer than any real Canva or share URL; caps what a stored, rendered string can be. */
    static final int MAX_URL_LENGTH = 2048;

    /**
     * What the owner's table lists. {@code CANCELED} stays visible, read-only, with its last link
     * (FR-1.4); {@code INCOMPLETE} never activated, so there is nothing to prove (FR-1.5).
     */
    private static final List<BundleSubscriptionStatus> OWNER_VISIBLE_STATUSES = List.of(
            BundleSubscriptionStatus.ACTIVE, BundleSubscriptionStatus.PAST_DUE, BundleSubscriptionStatus.CANCELED);

    private final ProofOfDisplayLinkRepository linkRepository;
    private final ProofOfDisplayEligibilityService eligibilityService;
    private final BundleSubscriptionRepository subscriptionRepository;
    private final BundleSubscriptionItemRepository itemRepository;
    private final BusinessRepository businessRepository;
    private final EmployeeRepository employeeRepository;
    private final BundleRepository bundleRepository;
    private final AdCampaignRepository adCampaignRepository;
    private final MediaRepository mediaRepository;
    private final JwtUtils jwtUtils;

    public ProofOfDisplayLinkService(ProofOfDisplayLinkRepository linkRepository,
                                     ProofOfDisplayEligibilityService eligibilityService,
                                     BundleSubscriptionRepository subscriptionRepository,
                                     BundleSubscriptionItemRepository itemRepository,
                                     BusinessRepository businessRepository,
                                     EmployeeRepository employeeRepository,
                                     BundleRepository bundleRepository,
                                     AdCampaignRepository adCampaignRepository,
                                     MediaRepository mediaRepository,
                                     JwtUtils jwtUtils) {
        this.linkRepository = linkRepository;
        this.eligibilityService = eligibilityService;
        this.subscriptionRepository = subscriptionRepository;
        this.itemRepository = itemRepository;
        this.businessRepository = businessRepository;
        this.employeeRepository = employeeRepository;
        this.bundleRepository = bundleRepository;
        this.adCampaignRepository = adCampaignRepository;
        this.mediaRepository = mediaRepository;
        this.jwtUtils = jwtUtils;
    }

    /**
     * Creates or replaces a subscription's link (FR-1). Checks run in this order so that a caller
     * who owns none of the subscription learns nothing about how it is split (P12-PROGRESS D2):
     * unknown subscription 404, caller owns none of its screens 403, multi-owner 409, not running
     * 409, malformed URL 400.
     */
    @Transactional
    public ProofOfDisplayLinkResponseModel upsertLink(Jwt jwt, String subscriptionId, String rawUrl) {
        String userId = jwtUtils.extractUserId(jwt);

        BundleSubscription subscription = subscriptionRepository.findBySubscriptionId(subscriptionId)
                .orElseThrow(() -> new BundleSubscriptionNotFoundException(subscriptionId));

        // The caller acts for their own business. Admins have no employee row and no screens, so
        // they cannot submit a link on an owner's behalf.
        Employee employee = employeeRepository.findByUserId(userId);
        if (employee == null || employee.getBusinessId() == null) {
            throw new AccessDeniedException("Access Denied");
        }
        String callerBusinessId = employee.getBusinessId().getBusinessId();

        Set<String> owners = eligibilityService.findOwnerBusinessIds(subscriptionId);
        if (!owners.contains(callerBusinessId)) {
            throw new AccessDeniedException("Access Denied");
        }
        if (!ProofOfDisplayEligibilityService.isSingleOwner(owners)) {
            throw new ProofLinkMultiOwnerException(subscriptionId);
        }
        if (!BundleSubscriptionStatus.LIVE.contains(subscription.getStatus())) {
            throw new ProofLinkNotEditableException(subscriptionId, subscription.getStatus());
        }

        String url = validateUrl(rawUrl);

        ProofOfDisplayLink link = linkRepository.findBySubscriptionId(subscriptionId)
                .orElseGet(() -> {
                    ProofOfDisplayLink created = new ProofOfDisplayLink();
                    created.setSubscriptionId(subscriptionId);
                    return created;
                });
        link.setUrl(url);
        link.setUpdatedByUserId(userId);
        link.setUpdatedAt(LocalDateTime.now());
        ProofOfDisplayLink saved = linkRepository.save(link);

        return new ProofOfDisplayLinkResponseModel(
                saved.getSubscriptionId(), saved.getUrl(), toOffset(saved.getUpdatedAt()));
    }

    /**
     * The media owner's Proof of Display table (FR-3.1): one row per subscription covering any of
     * their screens, multi-owner ones included so a running campaign never silently vanishes
     * (FR-3.2). Running subscriptions first, newest first within each group.
     */
    @Transactional(readOnly = true)
    public List<MediaOwnerProofSubscriptionResponseModel> getSubscriptionsForMediaOwner(Jwt jwt, String businessId) {
        jwtUtils.validateUserIsEmployeeOfBusiness(jwt, businessId);

        List<BundleSubscription> subscriptions =
                subscriptionRepository.findAllCoveringMediaOwnerBusiness(businessId, OWNER_VISIBLE_STATUSES);
        if (subscriptions.isEmpty()) {
            return List.of();
        }

        List<String> subscriptionIds = subscriptions.stream().map(BundleSubscription::getSubscriptionId).toList();
        List<BundleSubscriptionItem> items = itemRepository.findAllBySubscriptionIdIn(subscriptionIds);
        Map<String, Set<String>> ownersBySubscription = eligibilityService.ownerBusinessIdsBySubscription(items);
        Map<String, ProofOfDisplayLink> linksBySubscription = loadLinks(subscriptionIds);

        // Only this owner's screens are named: on a multi-owner subscription the rest are someone else's.
        Map<String, List<UUID>> ownMediaBySubscription = items.stream()
                .filter(item -> businessId.equals(item.getMediaOwnerBusinessId()))
                .collect(Collectors.groupingBy(BundleSubscriptionItem::getSubscriptionId,
                        Collectors.mapping(BundleSubscriptionItem::getMediaId, Collectors.toList())));
        Map<UUID, String> mediaTitles = mediaRepository.findAllById(
                        ownMediaBySubscription.values().stream().flatMap(List::stream).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Media::getId, Media::getTitle, (a, b) -> a));

        Map<String, Business> advertisers = businessRepository.findAllByBusinessId_BusinessIdIn(
                        subscriptions.stream().map(BundleSubscription::getAdvertiserBusinessId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(b -> b.getBusinessId().getBusinessId(), Function.identity(), (a, b) -> a));
        List<String> activeCampaignIds = advertisers.values().stream()
                .map(Business::getActiveCampaignId).filter(Objects::nonNull).distinct().toList();
        Map<String, String> campaignNames = activeCampaignIds.isEmpty()
                ? Map.of()
                : adCampaignRepository.findAllByCampaignId_CampaignIdIn(activeCampaignIds).stream()
                        .collect(Collectors.toMap(c -> c.getCampaignId().getCampaignId(), AdCampaign::getName,
                                (a, b) -> a));
        Map<String, Bundle> bundles = new HashMap<>();

        return subscriptions.stream()
                .sorted(Comparator
                        .comparing((BundleSubscription s) -> !BundleSubscriptionStatus.LIVE.contains(s.getStatus()))
                        .thenComparing(BundleSubscription::getCreatedAt,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .map(subscription -> {
                    String id = subscription.getSubscriptionId();
                    Business advertiser = advertisers.get(subscription.getAdvertiserBusinessId());
                    Bundle bundle = bundles.computeIfAbsent(subscription.getBundleId(),
                            bundleId -> bundleRepository.findByBundleId(bundleId).orElse(null));
                    ProofOfDisplayLink link = linksBySubscription.get(id);
                    ProofLinkResponseModel proofLink = toProofLink(ownersBySubscription.get(id), link);

                    MediaOwnerProofSubscriptionResponseModel row = new MediaOwnerProofSubscriptionResponseModel();
                    row.setSubscriptionId(id);
                    row.setSubscriptionStatus(subscription.getStatus());
                    row.setAdvertiserBusinessName(advertiser == null ? null : advertiser.getName());
                    if (bundle != null) {
                        row.setBundleNameEn(bundle.getNameEn());
                        row.setBundleNameFr(bundle.getNameFr());
                    }
                    row.setCampaignName(advertiser == null || advertiser.getActiveCampaignId() == null
                            ? null
                            : campaignNames.get(advertiser.getActiveCampaignId()));
                    row.setScreenNames(ownMediaBySubscription.getOrDefault(id, List.of()).stream()
                            .map(mediaTitles::get)
                            .filter(Objects::nonNull)
                            .sorted()
                            .toList());
                    row.setProofLink(proofLink);
                    row.setLinkUpdatedAt(proofLink.getStatus() == ProofLinkStatus.SET
                            ? toOffset(link.getUpdatedAt())
                            : null);
                    return row;
                })
                .toList();
    }

    /**
     * The {@code proofLink} for each of the given subscriptions, for the advertiser's
     * Subscriptions page (FR-2). Two queries however many subscriptions there are.
     */
    @Transactional(readOnly = true)
    public Map<String, ProofLinkResponseModel> getProofLinks(Collection<String> subscriptionIds) {
        if (subscriptionIds.isEmpty()) {
            return Map.of();
        }
        Map<String, Set<String>> ownersBySubscription = eligibilityService.ownerBusinessIdsBySubscription(
                itemRepository.findAllBySubscriptionIdIn(subscriptionIds));
        Map<String, ProofOfDisplayLink> linksBySubscription = loadLinks(subscriptionIds);

        Map<String, ProofLinkResponseModel> result = new HashMap<>();
        for (String subscriptionId : subscriptionIds) {
            result.put(subscriptionId,
                    toProofLink(ownersBySubscription.get(subscriptionId), linksBySubscription.get(subscriptionId)));
        }
        return result;
    }

    /**
     * FR-1.2 and P12-PROGRESS D8. The backend never fetches the URL, so there is no domain
     * allowlist (Canva sites can sit on custom domains). The scheme check is what stops a
     * {@code javascript:} link reaching the advertiser's dashboard.
     */
    static String validateUrl(String rawUrl) {
        String url = rawUrl == null ? "" : rawUrl.trim();
        if (url.isEmpty()) {
            throw new InvalidProofLinkUrlException("it is empty");
        }
        if (url.length() > MAX_URL_LENGTH) {
            throw new InvalidProofLinkUrlException("it is longer than " + MAX_URL_LENGTH + " characters");
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new InvalidProofLinkUrlException("it is not a well-formed URL");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new InvalidProofLinkUrlException("only http and https links are accepted");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new InvalidProofLinkUrlException("it has no host");
        }
        return url;
    }

    private Map<String, ProofOfDisplayLink> loadLinks(Collection<String> subscriptionIds) {
        return linkRepository.findBySubscriptionIdIn(subscriptionIds).stream()
                .collect(Collectors.toMap(ProofOfDisplayLink::getSubscriptionId, Function.identity()));
    }

    /** Multi-owner wins over a stored link: the PUT refuses them, so one can only be stale data. */
    private static ProofLinkResponseModel toProofLink(Set<String> owners, ProofOfDisplayLink link) {
        if (!ProofOfDisplayEligibilityService.isSingleOwner(owners)) {
            return new ProofLinkResponseModel(ProofLinkStatus.NOT_APPLICABLE, null);
        }
        if (link == null) {
            return new ProofLinkResponseModel(ProofLinkStatus.NOT_SET, null);
        }
        return new ProofLinkResponseModel(ProofLinkStatus.SET, link.getUrl());
    }

    private static OffsetDateTime toOffset(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
