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
        validateDiscountPercent(bundle.getDiscountPercent());
        bundle.setRuleValue(normalizeRuleValue(bundle.getRuleType(), bundle.getRuleValue()));
        return bundleRepository.save(bundle);
    }

    /**
     * Guards the DB CHECK with a 400 instead of a 500. Deliberately not capped at
     * the platform fee: a loss-leading promotion is a business call, and the admin
     * form warns past that threshold rather than blocking it.
     */
    private void validateDiscountPercent(int discountPercent) {
        if (discountPercent < 0 || discountPercent > 100) {
            throw new IllegalArgumentException(
                    "Discount percent must be between 0 and 100, got " + discountPercent);
        }
    }

    /**
     * FULL_NETWORK must carry no rule value — the DB CHECK enforces it, so normalise
     * here rather than letting a stale value trip the constraint. CITY/REGION/VENUE
     * require a genuine, non-blank value: {@code MediaSpecifications}' *EqualsIgnoreCase()
     * treats a blank value as "no filter", so a whitespace-only rule value would silently
     * turn the bundle into "every ACTIVE screen on the network" instead of the intended
     * slice — reject it with a 400 instead.
     */
    private String normalizeRuleValue(BundleRuleType ruleType, String ruleValue) {
        if (ruleType == BundleRuleType.FULL_NETWORK) {
            return null;
        }
        String normalized = ruleValue == null ? null : ruleValue.trim();
        if (normalized == null || normalized.isBlank()) {
            throw new IllegalArgumentException("ruleValue is required for ruleType " + ruleType);
        }
        return normalized;
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
        existing.setRuleValue(normalizeRuleValue(request.getRuleType(), request.getRuleValue()));
        if (request.getActive() != null) {
            existing.setActive(request.getActive());
        }
        // Absent means "no discount" rather than "leave unchanged", so clearing the
        // field in the admin form actually removes the discount.
        int discountPercent = request.getDiscountPercent() == null ? 0 : request.getDiscountPercent();
        validateDiscountPercent(discountPercent);
        existing.setDiscountPercent(discountPercent);

        return bundleRepository.save(existing);
    }

    @Override
    public void deleteBundle(String bundleId) {
        Bundle bundle = getBundleByBundleId(bundleId);

        long blocking = countBlockingSubscriptions(bundleId);
        if (blocking > 0) {
            throw new BundleHasActiveSubscriptionsException(bundleId, blocking);
        }

        // Only a bundle nobody has ever subscribed to is deletable (D47). This comment
        // previously claimed canceled-only history "cascades away with the bundle", following
        // brief req. 5 — both were wrong: bundle_subscriptions.bundle_id carries no ON DELETE
        // clause, so Postgres defaults to NO ACTION and refuses the delete. Verified against a
        // real migrated schema; the test schema has no foreign keys (D5) and cannot show it.
        // Exclusions do genuinely cascade — that FK is ON DELETE CASCADE.
        bundleRepository.delete(bundle);
    }

    @Override
    public List<Media> getRuleMatchedMedias(Bundle bundle) {
        return mediaRepository.findAll(ruleMatchedSpec(bundle, false));
    }

    @Override
    public List<Media> getCandidateMedias(Bundle bundle) {
        return mediaRepository.findAll(ruleMatchedSpec(bundle, true));
    }

    /**
     * ACTIVE media matching the bundle's rule. {@code fetchLocation} eager-fetches
     * mediaLocation for {@link #getCandidateMedias}, the one caller that reads
     * city/region per row (mediaLocation is LAZY, so without it every row would
     * trigger its own lazy-load query once the response mapper touches it) —
     * {@link #getRuleMatchedMedias} skips it deliberately: that method backs
     * {@code BundlePricingServiceImpl.quote()}, called once per bundle on every
     * public, unauthenticated {@code GET /bundles} listing, which never reads
     * location and shouldn't pay for the extra join on that hot path.
     */
    private Specification<Media> ruleMatchedSpec(Bundle bundle, boolean fetchLocation) {
        Specification<Media> spec = MediaSpecifications.hasStatus(Status.ACTIVE);
        if (fetchLocation) {
            spec = spec.and(MediaSpecifications.fetchMediaLocation());
        }

        Specification<Media> ruleSpec = switch (bundle.getRuleType()) {
            case FULL_NETWORK -> null;
            case CITY -> MediaSpecifications.cityEqualsIgnoreCase(bundle.getRuleValue());
            case REGION -> MediaSpecifications.regionEqualsIgnoreCase(bundle.getRuleValue());
            case VENUE -> MediaSpecifications.venueIdEquals(bundle.getRuleValue());
        };

        if (ruleSpec != null) {
            spec = spec.and(ruleSpec);
        }

        return spec;
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

    /**
     * Every subscription blocks the delete, whatever its status (D47) — matching the foreign key
     * rather than the narrower rule req. 5 described. Also feeds
     * {@code BundleResponseModel.activeSubscriptionCount}, which is what lets the admin delete
     * modal disable itself and explain why; widening both from one place keeps the warning and
     * the actual outcome from drifting apart.
     */
    @Override
    public long countBlockingSubscriptions(String bundleId) {
        return subscriptionRepository.countByBundleId(bundleId);
    }
}
