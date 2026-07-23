package com.envisionad.webservice.bundle.dataaccesslayer;

import com.envisionad.webservice.config.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Schema behaviour for {@code bundle_excluded_medias}, whose identity is the
 * (bundle, media) pair rather than a surrogate id.
 */
class BundleExcludedMediaRepositoryIntegrationTest extends BaseIntegrationTest {

    private static final String BUNDLE_ID = "bundle-id-1";
    private static final String OTHER_BUNDLE_ID = "bundle-id-2";

    @Autowired
    private BundleExcludedMediaRepository excludedMediaRepository;

    private UUID mediaId;
    private UUID otherMediaId;

    @BeforeEach
    void setUp() {
        excludedMediaRepository.deleteAll();
        mediaId = UUID.randomUUID();
        otherMediaId = UUID.randomUUID();
    }

    @Test
    void save_roundTripsTheCompositeKey() {
        excludedMediaRepository.save(new BundleExcludedMedia(BUNDLE_ID, mediaId));

        BundleExcludedMedia found = excludedMediaRepository
                .findById(new BundleExcludedMediaId(BUNDLE_ID, mediaId))
                .orElseThrow();

        assertEquals(BUNDLE_ID, found.getId().getBundleId());
        assertEquals(mediaId, found.getId().getMediaId());
    }

    @Test
    void findAllByIdBundleId_returnsOnlyThatBundlesExclusions() {
        excludedMediaRepository.save(new BundleExcludedMedia(BUNDLE_ID, mediaId));
        excludedMediaRepository.save(new BundleExcludedMedia(BUNDLE_ID, otherMediaId));
        excludedMediaRepository.save(new BundleExcludedMedia(OTHER_BUNDLE_ID, mediaId));

        List<BundleExcludedMedia> exclusions = excludedMediaRepository.findAllByIdBundleId(BUNDLE_ID);

        assertEquals(2, exclusions.size());
        assertTrue(exclusions.stream().allMatch(e -> BUNDLE_ID.equals(e.getId().getBundleId())));
    }

    @Test
    void existsByIdBundleIdAndIdMediaId_isScopedToTheExactPair() {
        excludedMediaRepository.save(new BundleExcludedMedia(BUNDLE_ID, mediaId));

        assertTrue(excludedMediaRepository.existsByIdBundleIdAndIdMediaId(BUNDLE_ID, mediaId));
        assertFalse(excludedMediaRepository.existsByIdBundleIdAndIdMediaId(BUNDLE_ID, otherMediaId));
        assertFalse(excludedMediaRepository.existsByIdBundleIdAndIdMediaId(OTHER_BUNDLE_ID, mediaId));
    }

    @Test
    void deleteByIdBundleIdAndIdMediaId_removesOnlyThatPair() {
        excludedMediaRepository.save(new BundleExcludedMedia(BUNDLE_ID, mediaId));
        excludedMediaRepository.save(new BundleExcludedMedia(BUNDLE_ID, otherMediaId));

        excludedMediaRepository.deleteByIdBundleIdAndIdMediaId(BUNDLE_ID, mediaId);

        List<BundleExcludedMedia> remaining = excludedMediaRepository.findAllByIdBundleId(BUNDLE_ID);
        assertEquals(1, remaining.size());
        assertEquals(otherMediaId, remaining.get(0).getId().getMediaId());
    }

    @Test
    void savingTheSamePairTwice_doesNotCreateASecondRow() {
        // The composite key makes a duplicate exclusion structurally impossible, but
        // note it surfaces as a JPA merge rather than a constraint violation — so the
        // observable guarantee is the row count, not an exception.
        excludedMediaRepository.save(new BundleExcludedMedia(BUNDLE_ID, mediaId));
        excludedMediaRepository.save(new BundleExcludedMedia(BUNDLE_ID, mediaId));

        assertEquals(1, excludedMediaRepository.findAllByIdBundleId(BUNDLE_ID).size());
    }

    @Test
    void compositeKeyEquality_isValueBased() {
        BundleExcludedMediaId first = new BundleExcludedMediaId(BUNDLE_ID, mediaId);
        BundleExcludedMediaId same = new BundleExcludedMediaId(BUNDLE_ID, mediaId);
        BundleExcludedMediaId different = new BundleExcludedMediaId(BUNDLE_ID, otherMediaId);

        assertEquals(first, same);
        assertEquals(first.hashCode(), same.hashCode());
        assertNotEquals(first, different);
    }
}
