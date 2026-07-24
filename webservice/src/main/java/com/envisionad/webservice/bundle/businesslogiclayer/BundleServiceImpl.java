package com.envisionad.webservice.bundle.businesslogiclayer;

import com.envisionad.webservice.bundle.dataaccesslayer.*;
import com.envisionad.webservice.bundle.exceptions.BundleHasActiveSubscriptionsException;
import com.envisionad.webservice.bundle.exceptions.BundleNotFoundException;
import com.envisionad.webservice.bundle.presentationlayer.models.BundleRequestModel;
import com.envisionad.webservice.media.DataAccessLayer.Media;
import com.envisionad.webservice.media.DataAccessLayer.MediaRepository;
import com.envisionad.webservice.media.DataAccessLayer.MediaSpecifications;
import com.envisionad.webservice.media.DataAccessLayer.Status;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionRepository;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class BundleServiceImpl implements BundleService {

    /** A bundle cannot be deleted while any subscription sits in one of these. */
    private static final List<BundleSubscriptionStatus> BLOCKING_STATUSES = List.of(
            BundleSubscriptionStatus.INCOMPLETE,
            BundleSubscriptionStatus.ACTIVE,
            BundleSubscriptionStatus.PAST_DUE);

    private final BundleRepository bundleRepository;
    private final BundleExcludedMediaRepository excludedMediaRepository;
    private final BundleSubscriptionRepository subscriptionRepository;
    private final MediaRepository mediaRepository;

    public BundleServiceImpl(BundleRepository bundleRepository,
            BundleExcludedMediaRepository excludedMediaRepository,
            BundleSubscriptionRepository subscriptionRepository,
            MediaRepository mediaRepository) {
        this.bundleRepository = bundleRepository;
        this.excludedMediaRepository = excludedMediaRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.mediaRepository = mediaRepository;
    }

    @Override
    public List<Bundle> getAllBundles(BundleRuleType ruleType, boolean activeOnly) {
        List<Bundle> bundles = ruleType != null
                ? bundleRepository.findAllByRuleType(ruleType)
                : bundleRepository.findAll();

        if (!activeOnly) {
            return bundles;
        }
        return bundles.stream().filter(Bundle::isActive).toList();
    }

    @Override
    public Bundle getBundleByBundleId(String bundleId) {
        return bundleRepository.findByBundleId(bundleId)
                .orElseThrow(() -> new BundleNotFoundException(bundleId));
    }

    @Override
    public Bundle createBundle(Bundle bundle) {
        return bundleRepository.save(bundle);
    }

    @Override
    public Bundle updateBundle(String bundleId, BundleRequestModel request) {
        Bundle existing = getBundleByBundleId(bundleId);

        existing.setNameEn(request.getNameEn());
        existing.setNameFr(request.getNameFr());
        existing.setDescriptionEn(request.getDescriptionEn());
        existing.setDescriptionFr(request.getDescriptionFr());
        existing.setIdealForEn(request.getIdealForEn());
        existing.setIdealForFr(request.getIdealForFr());
        existing.setBadgeColor(request.getBadgeColor());
        existing.setRuleType(request.getRuleType());
        // FULL_NETWORK must carry no rule value — the DB CHECK enforces it, so
        // normalise here rather than letting a stale value trip the constraint.
        existing.setRuleValue(request.getRuleType() == BundleRuleType.FULL_NETWORK
                ? null
                : request.getRuleValue());
        if (request.getActive() != null) {
            existing.setActive(request.getActive());
        }

        return bundleRepository.save(existing);
    }

    @Override
    public void deleteBundle(String bundleId) {
        Bundle bundle = getBundleByBundleId(bundleId);

        long blocking = countBlockingSubscriptions(bundleId);
        if (blocking > 0) {
            throw new BundleHasActiveSubscriptionsException(bundleId, blocking);
        }

        // Canceled-only history cascades away with the bundle, as do its exclusions.
        bundleRepository.delete(bundle);
    }

    @Override
    public List<Media> getRuleMatchedMedias(Bundle bundle) {
        Specification<Media> spec = MediaSpecifications.hasStatus(Status.ACTIVE);

        Specification<Media> ruleSpec = switch (bundle.getRuleType()) {
            case FULL_NETWORK -> null;
            case CITY -> MediaSpecifications.cityEqualsIgnoreCase(bundle.getRuleValue());
            case REGION -> MediaSpecifications.regionEqualsIgnoreCase(bundle.getRuleValue());
            case VENUE -> MediaSpecifications.venueIdEquals(bundle.getRuleValue());
        };

        if (ruleSpec != null) {
            spec = spec.and(ruleSpec);
        }

        return mediaRepository.findAll(spec);
    }

    @Override
    public Set<UUID> getExcludedMediaIds(String bundleId) {
        return excludedMediaRepository.findAllByIdBundleId(bundleId).stream()
                .map(excluded -> excluded.getId().getMediaId())
                .collect(Collectors.toSet());
    }

    @Override
    public void excludeMedia(String bundleId, UUID mediaId) {
        getBundleByBundleId(bundleId);
        if (!excludedMediaRepository.existsByIdBundleIdAndIdMediaId(bundleId, mediaId)) {
            excludedMediaRepository.save(new BundleExcludedMedia(bundleId, mediaId));
        }
    }

    @Override
    public void includeMedia(String bundleId, UUID mediaId) {
        getBundleByBundleId(bundleId);
        excludedMediaRepository.deleteByIdBundleIdAndIdMediaId(bundleId, mediaId);
    }

    @Override
    public long countBlockingSubscriptions(String bundleId) {
        return subscriptionRepository.countByBundleIdAndStatusIn(bundleId, BLOCKING_STATUSES);
    }
}
