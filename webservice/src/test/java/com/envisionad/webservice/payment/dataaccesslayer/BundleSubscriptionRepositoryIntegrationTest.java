package com.envisionad.webservice.payment.dataaccesslayer;

import com.envisionad.webservice.config.BaseIntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Schema behaviour for {@code bundle_subscriptions}, {@code bundle_subscription_items}
 * and {@code stripe_customers} — most importantly the partial unique index that limits
 * a business to one live subscription per bundle.
 *
 * <p>The test profile disables Flyway and builds the schema from the JPA entities
 * (ddl-auto: create), and a partial unique index cannot be expressed in JPA — so this
 * class creates the index itself, mirroring
 * {@code V20260717_001__add_bundles_and_subscriptions.sql}, and drops it afterwards.
 * The Testcontainers Postgres is shared by every integration class, so injected DDL
 * must not outlive this one.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BundleSubscriptionRepositoryIntegrationTest extends BaseIntegrationTest {

    /** Kept verbatim in sync with V20260717_001__add_bundles_and_subscriptions.sql. */
    private static final String CREATE_PARTIAL_INDEX = """
            CREATE UNIQUE INDEX IF NOT EXISTS uq_bundle_subscriptions_active_per_business
                ON bundle_subscriptions (bundle_id, advertiser_business_id)
                WHERE status IN ('INCOMPLETE', 'ACTIVE', 'PAST_DUE')
            """;

    private static final String DROP_PARTIAL_INDEX =
            "DROP INDEX IF EXISTS uq_bundle_subscriptions_active_per_business";

    private static final String BUNDLE_ID = "bundle-id-1";
    private static final String OTHER_BUNDLE_ID = "bundle-id-2";
    private static final String BUSINESS_ID = "business-id-1";
    private static final String OTHER_BUSINESS_ID = "business-id-2";
    private static final String CAMPAIGN_ID = "campaign-id-1";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BundleSubscriptionRepository subscriptionRepository;

    @Autowired
    private BundleSubscriptionItemRepository subscriptionItemRepository;

    @Autowired
    private StripeCustomerRepository stripeCustomerRepository;

    @BeforeAll
    void createPartialIndex() {
        jdbcTemplate.execute(CREATE_PARTIAL_INDEX);
    }

    @AfterAll
    void dropPartialIndex() {
        jdbcTemplate.execute(DROP_PARTIAL_INDEX);
    }

    @BeforeEach
    void setUp() {
        subscriptionItemRepository.deleteAll();
        subscriptionRepository.deleteAll();
        stripeCustomerRepository.deleteAll();
    }

    /**
     * Every call gets fresh values for the two <em>other</em> unique columns
     * ({@code subscription_id}, {@code stripe_checkout_session_id}) so that in the
     * duplicate-pair tests below the partial index is the only constraint that can
     * possibly fire. Reusing fixed values here would make those tests pass for the
     * wrong reason.
     */
    private BundleSubscription newSubscription(String bundleId,
            String businessId,
            BundleSubscriptionStatus status) {
        BundleSubscription subscription = new BundleSubscription();
        subscription.setSubscriptionId(UUID.randomUUID().toString());
        subscription.setBundleId(bundleId);
        subscription.setAdvertiserBusinessId(businessId);
        subscription.setCampaignId(CAMPAIGN_ID);
        subscription.setStripeCheckoutSessionId("cs_test_" + UUID.randomUUID());
        subscription.setStatus(status);
        subscription.setMonthlyAmount(new BigDecimal("120.00"));
        subscription.setScreenCount(30);
        return subscription;
    }

    // ---------- round trips ----------

    @Test
    void save_roundTripsEveryColumn() {
        BundleSubscription subscription = newSubscription(BUNDLE_ID, BUSINESS_ID, BundleSubscriptionStatus.ACTIVE);
        subscription.setStripeSubscriptionId("sub_test_1");
        LocalDateTime periodEnd = LocalDateTime.now().plusMonths(1).withNano(0);
        subscription.setCurrentPeriodEnd(periodEnd);
        subscription.setCancelAtPeriodEnd(true);
        subscription.setCanceledAt(periodEnd);

        BundleSubscription saved = subscriptionRepository.save(subscription);
        assertNotNull(saved.getId());

        BundleSubscription found = subscriptionRepository
                .findBySubscriptionId(saved.getSubscriptionId()).orElseThrow();

        assertEquals(BUNDLE_ID, found.getBundleId());
        assertEquals(BUSINESS_ID, found.getAdvertiserBusinessId());
        assertEquals(CAMPAIGN_ID, found.getCampaignId());
        assertEquals("sub_test_1", found.getStripeSubscriptionId());
        assertEquals(subscription.getStripeCheckoutSessionId(), found.getStripeCheckoutSessionId());
        assertEquals(BundleSubscriptionStatus.ACTIVE, found.getStatus());
        assertEquals(0, new BigDecimal("120.00").compareTo(found.getMonthlyAmount()));
        assertEquals(30, found.getScreenCount());
        assertEquals(periodEnd, found.getCurrentPeriodEnd());
        assertTrue(found.isCancelAtPeriodEnd());
        assertEquals(periodEnd, found.getCanceledAt());
        assertNotNull(found.getCreatedAt());
    }

    @Test
    void save_generatesSubscriptionIdWhenAbsent() {
        BundleSubscription subscription = newSubscription(BUNDLE_ID, BUSINESS_ID, BundleSubscriptionStatus.INCOMPLETE);
        subscription.setSubscriptionId(null);

        BundleSubscription saved = subscriptionRepository.save(subscription);

        assertNotNull(saved.getSubscriptionId());
        assertDoesNotThrow(() -> UUID.fromString(saved.getSubscriptionId()));
    }

    @Test
    void save_defaultsCancelAtPeriodEndToFalse_andLeavesStripeSubscriptionIdNull() {
        BundleSubscription saved = subscriptionRepository.save(
                newSubscription(BUNDLE_ID, BUSINESS_ID, BundleSubscriptionStatus.INCOMPLETE));

        BundleSubscription found = subscriptionRepository
                .findBySubscriptionId(saved.getSubscriptionId()).orElseThrow();

        assertFalse(found.isCancelAtPeriodEnd());
        assertNull(found.getStripeSubscriptionId(), "null until checkout.session.completed links it");
        assertNull(found.getCurrentPeriodEnd());
        assertNull(found.getCanceledAt());
    }

    @Test
    void finders_locateSubscriptionsByEachStripeIdentifier() {
        BundleSubscription subscription = newSubscription(BUNDLE_ID, BUSINESS_ID, BundleSubscriptionStatus.ACTIVE);
        subscription.setStripeSubscriptionId("sub_test_lookup");
        BundleSubscription saved = subscriptionRepository.save(subscription);

        assertTrue(subscriptionRepository
                .findByStripeCheckoutSessionId(saved.getStripeCheckoutSessionId()).isPresent());
        assertTrue(subscriptionRepository.findByStripeSubscriptionId("sub_test_lookup").isPresent());
        assertTrue(subscriptionRepository.findByStripeSubscriptionId("sub_test_missing").isEmpty());
        assertTrue(subscriptionRepository.findBySubscriptionId("no-such-subscription").isEmpty());
    }

    @Test
    void findAllByAdvertiserBusinessId_returnsOnlyThatBusinesses() {
        subscriptionRepository.save(newSubscription(BUNDLE_ID, BUSINESS_ID, BundleSubscriptionStatus.ACTIVE));
        subscriptionRepository.save(newSubscription(OTHER_BUNDLE_ID, BUSINESS_ID, BundleSubscriptionStatus.ACTIVE));
        subscriptionRepository.save(newSubscription(BUNDLE_ID, OTHER_BUSINESS_ID, BundleSubscriptionStatus.ACTIVE));

        assertEquals(2, subscriptionRepository.findAllByAdvertiserBusinessId(BUSINESS_ID).size());
        assertEquals(1, subscriptionRepository.findAllByAdvertiserBusinessId(OTHER_BUSINESS_ID).size());
    }

    @Test
    void findAllByBundleIdAndStatusIn_filtersOnBothAxes() {
        subscriptionRepository.save(newSubscription(BUNDLE_ID, BUSINESS_ID, BundleSubscriptionStatus.ACTIVE));
        subscriptionRepository.save(newSubscription(BUNDLE_ID, OTHER_BUSINESS_ID, BundleSubscriptionStatus.CANCELED));
        subscriptionRepository.save(newSubscription(OTHER_BUNDLE_ID, BUSINESS_ID, BundleSubscriptionStatus.ACTIVE));

        List<BundleSubscription> blocking = subscriptionRepository.findAllByBundleIdAndStatusIn(
                BUNDLE_ID, List.of(BundleSubscriptionStatus.INCOMPLETE,
                        BundleSubscriptionStatus.ACTIVE,
                        BundleSubscriptionStatus.PAST_DUE));

        assertEquals(1, blocking.size());
        assertEquals(BUSINESS_ID, blocking.get(0).getAdvertiserBusinessId());
    }

    @Test
    void subscriptionItems_roundTripAndLoadBySubscription() {
        BundleSubscription subscription = subscriptionRepository.save(
                newSubscription(BUNDLE_ID, BUSINESS_ID, BundleSubscriptionStatus.ACTIVE));

        UUID mediaId = UUID.randomUUID();
        BundleSubscriptionItem item = new BundleSubscriptionItem();
        item.setSubscriptionId(subscription.getSubscriptionId());
        item.setMediaId(mediaId);
        item.setMediaOwnerBusinessId("owner-business-id");
        item.setMonthlyAmount(new BigDecimal("4.00"));
        subscriptionItemRepository.save(item);

        BundleSubscriptionItem other = new BundleSubscriptionItem();
        other.setSubscriptionId("some-other-subscription");
        other.setMediaId(UUID.randomUUID());
        other.setMediaOwnerBusinessId("owner-business-id");
        other.setMonthlyAmount(new BigDecimal("4.00"));
        subscriptionItemRepository.save(other);

        List<BundleSubscriptionItem> items =
                subscriptionItemRepository.findAllBySubscriptionId(subscription.getSubscriptionId());

        assertEquals(1, items.size());
        assertEquals(mediaId, items.get(0).getMediaId());
        assertEquals("owner-business-id", items.get(0).getMediaOwnerBusinessId());
        assertEquals(0, new BigDecimal("4.00").compareTo(items.get(0).getMonthlyAmount()));
    }

    @Test
    void stripeCustomer_roundTripsAndIsUniquePerBusiness() {
        StripeCustomer customer = new StripeCustomer();
        customer.setBusinessId(BUSINESS_ID);
        customer.setStripeCustomerId("cus_test_1");
        StripeCustomer saved = stripeCustomerRepository.save(customer);

        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());

        StripeCustomer found = stripeCustomerRepository.findByBusinessId(BUSINESS_ID).orElseThrow();
        assertEquals("cus_test_1", found.getStripeCustomerId());
        assertTrue(stripeCustomerRepository.findByStripeCustomerId("cus_test_1").isPresent());
        assertTrue(stripeCustomerRepository.findByBusinessId(OTHER_BUSINESS_ID).isEmpty());

        StripeCustomer duplicate = new StripeCustomer();
        duplicate.setBusinessId(BUSINESS_ID);
        duplicate.setStripeCustomerId("cus_test_2");

        assertThrows(DataIntegrityViolationException.class,
                () -> stripeCustomerRepository.saveAndFlush(duplicate));
    }

    // ---------- the partial unique index ----------

    @Test
    void secondIncompleteSubscriptionForSamePair_isRejected() {
        subscriptionRepository.save(newSubscription(BUNDLE_ID, BUSINESS_ID, BundleSubscriptionStatus.INCOMPLETE));

        assertThrows(DataIntegrityViolationException.class, () -> subscriptionRepository
                .saveAndFlush(newSubscription(BUNDLE_ID, BUSINESS_ID, BundleSubscriptionStatus.INCOMPLETE)));
    }

    @Test
    void secondActiveSubscriptionForSamePair_isRejected() {
        subscriptionRepository.save(newSubscription(BUNDLE_ID, BUSINESS_ID, BundleSubscriptionStatus.ACTIVE));

        assertThrows(DataIntegrityViolationException.class, () -> subscriptionRepository
                .saveAndFlush(newSubscription(BUNDLE_ID, BUSINESS_ID, BundleSubscriptionStatus.ACTIVE)));
    }

    @Test
    void mixedLiveStatusesForSamePair_areRejected() {
        subscriptionRepository.save(newSubscription(BUNDLE_ID, BUSINESS_ID, BundleSubscriptionStatus.INCOMPLETE));

        assertThrows(DataIntegrityViolationException.class, () -> subscriptionRepository
                .saveAndFlush(newSubscription(BUNDLE_ID, BUSINESS_ID, BundleSubscriptionStatus.PAST_DUE)));
    }

    @Test
    void canceledSubscriptionDoesNotBlockANewOne_provingTheIndexIsPartial() {
        subscriptionRepository.save(newSubscription(BUNDLE_ID, BUSINESS_ID, BundleSubscriptionStatus.CANCELED));

        assertDoesNotThrow(() -> subscriptionRepository
                .saveAndFlush(newSubscription(BUNDLE_ID, BUSINESS_ID, BundleSubscriptionStatus.INCOMPLETE)));

        assertEquals(2, subscriptionRepository.findAllByAdvertiserBusinessId(BUSINESS_ID).size());
    }

    @Test
    void multipleCanceledSubscriptionsForSamePair_areAllowed() {
        subscriptionRepository.save(newSubscription(BUNDLE_ID, BUSINESS_ID, BundleSubscriptionStatus.CANCELED));

        assertDoesNotThrow(() -> subscriptionRepository
                .saveAndFlush(newSubscription(BUNDLE_ID, BUSINESS_ID, BundleSubscriptionStatus.CANCELED)));
    }

    @Test
    void sameBundleDifferentBusiness_isAllowed() {
        subscriptionRepository.save(newSubscription(BUNDLE_ID, BUSINESS_ID, BundleSubscriptionStatus.ACTIVE));

        assertDoesNotThrow(() -> subscriptionRepository
                .saveAndFlush(newSubscription(BUNDLE_ID, OTHER_BUSINESS_ID, BundleSubscriptionStatus.ACTIVE)));
    }

    @Test
    void differentBundleSameBusiness_isAllowed() {
        subscriptionRepository.save(newSubscription(BUNDLE_ID, BUSINESS_ID, BundleSubscriptionStatus.ACTIVE));

        assertDoesNotThrow(() -> subscriptionRepository
                .saveAndFlush(newSubscription(OTHER_BUNDLE_ID, BUSINESS_ID, BundleSubscriptionStatus.ACTIVE)));
    }

    @Test
    void cancelingALiveSubscriptionFreesThePairForResubscription() {
        BundleSubscription live = subscriptionRepository.save(
                newSubscription(BUNDLE_ID, BUSINESS_ID, BundleSubscriptionStatus.ACTIVE));

        live.setStatus(BundleSubscriptionStatus.CANCELED);
        live.setCanceledAt(LocalDateTime.now());
        subscriptionRepository.saveAndFlush(live);

        assertDoesNotThrow(() -> subscriptionRepository
                .saveAndFlush(newSubscription(BUNDLE_ID, BUSINESS_ID, BundleSubscriptionStatus.INCOMPLETE)));
    }
}
