package com.envisionad.webservice.bundle.businesslogiclayer;

import com.envisionad.webservice.bundle.dataaccesslayer.Bundle;
import com.envisionad.webservice.bundle.dataaccesslayer.BundleRuleType;
import com.envisionad.webservice.bundle.presentationlayer.models.BundleRequestModel;
import com.envisionad.webservice.media.DataAccessLayer.Media;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface BundleService {

    List<Bundle> getAllBundles(BundleRuleType ruleType, boolean activeOnly);

    Bundle getBundleByBundleId(String bundleId);

    Bundle createBundle(Bundle bundle);

    Bundle updateBundle(String bundleId, BundleRequestModel request);

    void deleteBundle(String bundleId);

    /**
     * ACTIVE media matching the bundle's rule, <strong>before</strong> manual
     * exclusions are applied. Exclusions are subtracted only by
     * {@link ManualExclusionFilter}; the admin exclusions modal needs the full set
     * so it can show excluded media flagged rather than missing.
     */
    List<Media> getRuleMatchedMedias(Bundle bundle);

    /**
     * Same rule match as {@link #getRuleMatchedMedias}, for the admin candidate-medias
     * screen specifically: eager-fetches {@code mediaLocation} so the city/region
     * columns it renders per row don't each trigger their own lazy-load query. Kept
     * separate from {@link #getRuleMatchedMedias} because that method backs the
     * pricing quote used on every public bundle listing, which never reads location.
     */
    List<Media> getCandidateMedias(Bundle bundle);

    Set<UUID> getExcludedMediaIds(String bundleId);

    void excludeMedia(String bundleId, UUID mediaId);

    void includeMedia(String bundleId, UUID mediaId);

    /** Subscriptions in INCOMPLETE/ACTIVE/PAST_DUE — the set that blocks deletion. */
    long countBlockingSubscriptions(String bundleId);
}
