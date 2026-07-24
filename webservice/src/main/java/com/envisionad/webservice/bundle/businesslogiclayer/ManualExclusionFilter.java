package com.envisionad.webservice.bundle.businesslogiclayer;

import com.envisionad.webservice.bundle.dataaccesslayer.BundleExcludedMedia;
import com.envisionad.webservice.bundle.dataaccesslayer.BundleExcludedMediaRepository;
import com.envisionad.webservice.media.DataAccessLayer.Media;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Drops media the admin has manually excluded from this specific bundle.
 *
 * <p>This is the <em>only</em> place exclusions are subtracted. The rule-matching
 * query deliberately returns the pre-exclusion set, because the admin exclusions
 * modal needs to show excluded media too (flagged rather than hidden).
 */
@Component
public class ManualExclusionFilter implements MediaEligibilityFilter {

    private final BundleExcludedMediaRepository excludedMediaRepository;

    public ManualExclusionFilter(BundleExcludedMediaRepository excludedMediaRepository) {
        this.excludedMediaRepository = excludedMediaRepository;
    }

    @Override
    public List<Media> filter(List<Media> candidateMedias, PricingContext context) {
        if (candidateMedias.isEmpty()) {
            return candidateMedias;
        }

        Set<UUID> excludedMediaIds = excludedMediaRepository
                .findAllByIdBundleId(context.bundleId())
                .stream()
                .map(BundleExcludedMedia::getId)
                .map(id -> id.getMediaId())
                .collect(Collectors.toSet());

        if (excludedMediaIds.isEmpty()) {
            return candidateMedias;
        }

        return candidateMedias.stream()
                .filter(media -> !excludedMediaIds.contains(media.getId()))
                .toList();
    }
}
