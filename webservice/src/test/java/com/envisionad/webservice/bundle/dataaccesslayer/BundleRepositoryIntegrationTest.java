package com.envisionad.webservice.bundle.dataaccesslayer;

import com.envisionad.webservice.config.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Schema behaviour for the {@code bundles} table.
 *
 * <p>The chk_bundles_rule_value CHECK is the one constraint from
 * V20260717_001 that reaches the test schema on its own — it is declared as
 * {@code @Check} on {@link Bundle}, so Hibernate emits it under ddl-auto: create.
 * The partial unique index and the campaign-delete trigger cannot be expressed in
 * JPA and are covered by sibling tests that inject their DDL.
 */
class BundleRepositoryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private BundleRepository bundleRepository;

    @BeforeEach
    void setUp() {
        bundleRepository.deleteAll();
    }

    private Bundle newBundle(BundleRuleType ruleType, String ruleValue) {
        Bundle bundle = new Bundle();
        bundle.setNameEn("Full Network");
        bundle.setNameFr("Réseau complet");
        bundle.setDescriptionEn("Every active screen on the network.");
        bundle.setDescriptionFr("Tous les écrans actifs du réseau.");
        bundle.setIdealForEn("Local retailers, gyms, cafés");
        bundle.setIdealForFr("Détaillants locaux, gyms, cafés");
        bundle.setBadgeColor("#FF5733");
        bundle.setRuleType(ruleType);
        bundle.setRuleValue(ruleValue);
        return bundle;
    }

    @Test
    void save_roundTripsEveryColumn_andGeneratesIdentifiers() {
        Bundle saved = bundleRepository.save(newBundle(BundleRuleType.CITY, "Montreal"));

        assertNotNull(saved.getId());
        assertNotNull(saved.getBundleId(), "bundleId should be generated in @PrePersist");
        assertDoesNotThrow(() -> java.util.UUID.fromString(saved.getBundleId()));

        Bundle found = bundleRepository.findByBundleId(saved.getBundleId()).orElseThrow();
        assertEquals("Full Network", found.getNameEn());
        assertEquals("Réseau complet", found.getNameFr());
        assertEquals("Every active screen on the network.", found.getDescriptionEn());
        assertEquals("Tous les écrans actifs du réseau.", found.getDescriptionFr());
        assertEquals("Local retailers, gyms, cafés", found.getIdealForEn());
        assertEquals("Détaillants locaux, gyms, cafés", found.getIdealForFr());
        assertEquals("#FF5733", found.getBadgeColor());
        assertEquals(BundleRuleType.CITY, found.getRuleType());
        assertEquals("Montreal", found.getRuleValue());
        assertTrue(found.isActive(), "bundles default to active");
        assertNotNull(found.getCreatedAt());
        assertNotNull(found.getUpdatedAt());
    }

    @Test
    void save_keepsCallerSuppliedBundleId() {
        Bundle bundle = newBundle(BundleRuleType.FULL_NETWORK, null);
        bundle.setBundleId("fixed-bundle-id");

        Bundle saved = bundleRepository.save(bundle);

        assertEquals("fixed-bundle-id", saved.getBundleId());
    }

    @Test
    void findAllByActiveTrue_excludesDeactivatedBundles() {
        bundleRepository.save(newBundle(BundleRuleType.CITY, "Montreal"));

        Bundle inactive = newBundle(BundleRuleType.CITY, "Laval");
        inactive.setActive(false);
        bundleRepository.save(inactive);

        List<Bundle> active = bundleRepository.findAllByActiveTrue();

        assertEquals(1, active.size());
        assertEquals("Montreal", active.get(0).getRuleValue());
    }

    @Test
    void findAllByRuleType_filtersByRuleType() {
        bundleRepository.save(newBundle(BundleRuleType.CITY, "Montreal"));
        bundleRepository.save(newBundle(BundleRuleType.REGION, "Montérégie"));
        bundleRepository.save(newBundle(BundleRuleType.FULL_NETWORK, null));

        assertEquals(1, bundleRepository.findAllByRuleType(BundleRuleType.CITY).size());
        assertEquals(1, bundleRepository.findAllByRuleType(BundleRuleType.REGION).size());
        assertEquals(1, bundleRepository.findAllByRuleType(BundleRuleType.FULL_NETWORK).size());
        assertTrue(bundleRepository.findAllByRuleType(BundleRuleType.VENUE).isEmpty());
    }

    @Test
    void existsByBundleId_reflectsPersistence() {
        Bundle saved = bundleRepository.save(newBundle(BundleRuleType.VENUE, "venue-id-1"));

        assertTrue(bundleRepository.existsByBundleId(saved.getBundleId()));
        assertFalse(bundleRepository.existsByBundleId("no-such-bundle"));
    }

    @Test
    void findByBundleId_returnsEmptyWhenAbsent() {
        assertEquals(Optional.empty(), bundleRepository.findByBundleId("no-such-bundle"));
    }

    @Test
    void save_fullNetworkWithRuleValue_isRejectedByCheckConstraint() {
        Bundle invalid = newBundle(BundleRuleType.FULL_NETWORK, "Montreal");

        assertThrows(DataIntegrityViolationException.class,
                () -> bundleRepository.saveAndFlush(invalid),
                "FULL_NETWORK must not carry a rule value");
    }

    @Test
    void save_ruleTypeWithoutRuleValue_isRejectedByCheckConstraint() {
        Bundle invalid = newBundle(BundleRuleType.CITY, null);

        assertThrows(DataIntegrityViolationException.class,
                () -> bundleRepository.saveAndFlush(invalid),
                "CITY/REGION/VENUE must carry a rule value");
    }

    @Test
    void save_duplicateBundleId_isRejected() {
        Bundle first = newBundle(BundleRuleType.CITY, "Montreal");
        first.setBundleId("duplicate-id");
        bundleRepository.save(first);

        Bundle second = newBundle(BundleRuleType.CITY, "Laval");
        second.setBundleId("duplicate-id");

        assertThrows(DataIntegrityViolationException.class,
                () -> bundleRepository.saveAndFlush(second));
    }
}
