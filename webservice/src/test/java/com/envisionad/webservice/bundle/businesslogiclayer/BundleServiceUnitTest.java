package com.envisionad.webservice.bundle.businesslogiclayer;

import com.envisionad.webservice.bundle.dataaccesslayer.*;
import com.envisionad.webservice.bundle.exceptions.BundleHasActiveSubscriptionsException;
import com.envisionad.webservice.bundle.exceptions.BundleNotFoundException;
import com.envisionad.webservice.bundle.presentationlayer.models.BundleRequestModel;
import com.envisionad.webservice.media.DataAccessLayer.MediaRepository;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionRepository;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * CRUD, the delete guard, and exclusion toggling. Rule matching itself is exercised
 * against real data in {@code BundleControllerIntegrationTest} — asserting here that
 * "a Specification was built" would prove nothing about what it selects.
 */
@ExtendWith(MockitoExtension.class)
class BundleServiceUnitTest {

    private static final String BUNDLE_ID = "bundle-1";

    @InjectMocks
    private BundleServiceImpl bundleService;

    @Mock
    private BundleRepository bundleRepository;

    @Mock
    private BundleExcludedMediaRepository excludedMediaRepository;

    @Mock
    private BundleSubscriptionRepository subscriptionRepository;

    @Mock
    private MediaRepository mediaRepository;

    private Bundle bundle;

    @BeforeEach
    void setUp() {
        bundle = new Bundle();
        bundle.setId(1L);
        bundle.setBundleId(BUNDLE_ID);
        bundle.setNameEn("Montreal");
        bundle.setNameFr("Montréal");
        bundle.setBadgeColor("#FF5733");
        bundle.setRuleType(BundleRuleType.CITY);
        bundle.setRuleValue("Montreal");
    }

    private BundleRequestModel request(BundleRuleType ruleType, String ruleValue) {
        BundleRequestModel request = new BundleRequestModel();
        request.setNameEn("Updated EN");
        request.setNameFr("Updated FR");
        request.setDescriptionEn("Desc EN");
        request.setDescriptionFr("Desc FR");
        request.setIdealForEn("Ideal EN");
        request.setIdealForFr("Ideal FR");
        request.setBadgeColor("#000000");
        request.setRuleType(ruleType);
        request.setRuleValue(ruleValue);
        return request;
    }

    @Test
    void getBundleByBundleId_returnsTheBundle() {
        when(bundleRepository.findByBundleId(BUNDLE_ID)).thenReturn(Optional.of(bundle));

        assertEquals(bundle, bundleService.getBundleByBundleId(BUNDLE_ID));
    }

    @Test
    void getBundleByBundleId_throwsWhenAbsent() {
        when(bundleRepository.findByBundleId("nope")).thenReturn(Optional.empty());

        assertThrows(BundleNotFoundException.class, () -> bundleService.getBundleByBundleId("nope"));
    }

    @Test
    void getAllBundles_withNoRuleType_returnsEverything() {
        Bundle inactive = new Bundle();
        inactive.setActive(false);
        when(bundleRepository.findAll()).thenReturn(List.of(bundle, inactive));

        assertEquals(2, bundleService.getAllBundles(null, false).size());
    }

    @Test
    void getAllBundles_activeOnly_filtersOutDeactivated() {
        Bundle inactive = new Bundle();
        inactive.setActive(false);
        when(bundleRepository.findAll()).thenReturn(List.of(bundle, inactive));

        List<Bundle> result = bundleService.getAllBundles(null, true);

        assertEquals(1, result.size());
        assertEquals(BUNDLE_ID, result.get(0).getBundleId());
    }

    @Test
    void getAllBundles_withRuleType_delegatesToTheTypedFinder() {
        when(bundleRepository.findAllByRuleType(BundleRuleType.CITY)).thenReturn(List.of(bundle));

        assertEquals(1, bundleService.getAllBundles(BundleRuleType.CITY, true).size());
        verify(bundleRepository, never()).findAll();
    }

    @Test
    void createBundle_savesAsGiven() {
        when(bundleRepository.save(bundle)).thenReturn(bundle);

        assertEquals(bundle, bundleService.createBundle(bundle));
    }

    @Test
    void updateBundle_copiesEveryEditableField() {
        when(bundleRepository.findByBundleId(BUNDLE_ID)).thenReturn(Optional.of(bundle));
        when(bundleRepository.save(any(Bundle.class))).thenAnswer(inv -> inv.getArgument(0));

        Bundle updated = bundleService.updateBundle(BUNDLE_ID, request(BundleRuleType.REGION, "Montérégie"));

        assertEquals("Updated EN", updated.getNameEn());
        assertEquals("Updated FR", updated.getNameFr());
        assertEquals("Desc EN", updated.getDescriptionEn());
        assertEquals("Ideal FR", updated.getIdealForFr());
        assertEquals("#000000", updated.getBadgeColor());
        assertEquals(BundleRuleType.REGION, updated.getRuleType());
        assertEquals("Montérégie", updated.getRuleValue());
    }

    @Test
    void updateBundle_toFullNetwork_clearsAStaleRuleValue() {
        // Otherwise the DB CHECK constraint would reject the write.
        when(bundleRepository.findByBundleId(BUNDLE_ID)).thenReturn(Optional.of(bundle));
        when(bundleRepository.save(any(Bundle.class))).thenAnswer(inv -> inv.getArgument(0));

        Bundle updated = bundleService.updateBundle(
                BUNDLE_ID, request(BundleRuleType.FULL_NETWORK, "Montreal"));

        assertNull(updated.getRuleValue());
    }

    @Test
    void updateBundle_appliesActiveOnlyWhenSupplied() {
        when(bundleRepository.findByBundleId(BUNDLE_ID)).thenReturn(Optional.of(bundle));
        when(bundleRepository.save(any(Bundle.class))).thenAnswer(inv -> inv.getArgument(0));

        BundleRequestModel withoutActive = request(BundleRuleType.CITY, "Laval");
        assertTrue(bundleService.updateBundle(BUNDLE_ID, withoutActive).isActive());

        BundleRequestModel deactivating = request(BundleRuleType.CITY, "Laval");
        deactivating.setActive(false);
        assertFalse(bundleService.updateBundle(BUNDLE_ID, deactivating).isActive());
    }

    @Test
    void deleteBundle_succeedsWhenNoSubscriptionHasEverReferencedIt() {
        when(bundleRepository.findByBundleId(BUNDLE_ID)).thenReturn(Optional.of(bundle));
        when(subscriptionRepository.countByBundleId(BUNDLE_ID)).thenReturn(0L);

        bundleService.deleteBundle(BUNDLE_ID);

        verify(bundleRepository).delete(bundle);
    }

    @Test
    void deleteBundle_isBlockedBySubscriptions() {
        when(bundleRepository.findByBundleId(BUNDLE_ID)).thenReturn(Optional.of(bundle));
        when(subscriptionRepository.countByBundleId(BUNDLE_ID)).thenReturn(3L);

        BundleHasActiveSubscriptionsException thrown = assertThrows(
                BundleHasActiveSubscriptionsException.class,
                () -> bundleService.deleteBundle(BUNDLE_ID));

        assertTrue(thrown.getMessage().contains("3"), "the message should name the blocking count");
        verify(bundleRepository, never()).delete(any());
    }

    /**
     * D47: the guard counts every subscription, whatever its status. It used to scope to
     * INCOMPLETE/ACTIVE/PAST_DUE on the strength of brief req. 5's "canceled-only history
     * cascades away with the bundle" — but {@code bundle_subscriptions.bundle_id} has no
     * {@code ON DELETE} clause, so Postgres refuses that delete. Verified against a real
     * migrated schema; invisible here because the test schema has no foreign keys (D5).
     */
    @Test
    void deleteBundle_isBlockedByCanceledHistoryToo() {
        when(bundleRepository.findByBundleId(BUNDLE_ID)).thenReturn(Optional.of(bundle));
        when(subscriptionRepository.countByBundleId(BUNDLE_ID)).thenReturn(1L);

        assertThrows(BundleHasActiveSubscriptionsException.class,
                () -> bundleService.deleteBundle(BUNDLE_ID));

        verify(bundleRepository, never()).delete(any());
    }

    @Test
    void excludeMedia_writesTheExclusionOnce() {
        UUID mediaId = UUID.randomUUID();
        when(bundleRepository.findByBundleId(BUNDLE_ID)).thenReturn(Optional.of(bundle));
        when(excludedMediaRepository.existsByIdBundleIdAndIdMediaId(BUNDLE_ID, mediaId)).thenReturn(false);

        bundleService.excludeMedia(BUNDLE_ID, mediaId);

        verify(excludedMediaRepository).save(new BundleExcludedMedia(BUNDLE_ID, mediaId));
    }

    @Test
    void excludeMedia_isIdempotent() {
        UUID mediaId = UUID.randomUUID();
        when(bundleRepository.findByBundleId(BUNDLE_ID)).thenReturn(Optional.of(bundle));
        when(excludedMediaRepository.existsByIdBundleIdAndIdMediaId(BUNDLE_ID, mediaId)).thenReturn(true);

        bundleService.excludeMedia(BUNDLE_ID, mediaId);

        verify(excludedMediaRepository, never()).save(any());
    }

    @Test
    void excludeMedia_onUnknownBundle_throws() {
        when(bundleRepository.findByBundleId("nope")).thenReturn(Optional.empty());

        assertThrows(BundleNotFoundException.class,
                () -> bundleService.excludeMedia("nope", UUID.randomUUID()));
        verify(excludedMediaRepository, never()).save(any());
    }

    @Test
    void includeMedia_removesTheExclusion() {
        UUID mediaId = UUID.randomUUID();
        when(bundleRepository.findByBundleId(BUNDLE_ID)).thenReturn(Optional.of(bundle));

        bundleService.includeMedia(BUNDLE_ID, mediaId);

        verify(excludedMediaRepository).deleteByIdBundleIdAndIdMediaId(BUNDLE_ID, mediaId);
    }

    @Test
    void getExcludedMediaIds_unwrapsTheCompositeKeys() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(excludedMediaRepository.findAllByIdBundleId(BUNDLE_ID)).thenReturn(List.of(
                new BundleExcludedMedia(BUNDLE_ID, first),
                new BundleExcludedMedia(BUNDLE_ID, second)));

        assertEquals(Set.of(first, second), bundleService.getExcludedMediaIds(BUNDLE_ID));
    }
}
