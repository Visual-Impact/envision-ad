package com.envisionad.webservice.advertisement.dataaccesslayer;

import com.envisionad.webservice.business.dataaccesslayer.Business;
import com.envisionad.webservice.business.dataaccesslayer.BusinessIdentifier;
import com.envisionad.webservice.business.dataaccesslayer.BusinessRepository;
import com.envisionad.webservice.config.BaseIntegrationTest;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscription;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionRepository;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionStatus;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Behaviour of the {@code prevent_active_campaign_delete()} guard after the P6 follow-up:
 * a campaign that is a business's {@code active_campaign_id} cannot be deleted; nothing else
 * blocks a delete.
 *
 * <p>History: introduced by V20260714_001 (reservations), extended by V20260717_001, reduced
 * to a single {@code bundle_subscriptions} clause by V20260801_001 (M6), extended again to two
 * clauses by V20260822_001 (P6 M1), and reduced by V20260831_001 (this PR) to the single
 * {@code business.active_campaign_id} clause — the live-{@code bundle_subscriptions} clause
 * became unreachable once {@code bundle_subscriptions.campaign_id} was dropped.
 *
 * <p>The function and trigger are plpgsql and so are absent from the entity-generated test
 * schema; this class installs them, mirroring the migration, and removes them afterwards.
 * Dropping in {@code @AfterAll} is not optional — the Testcontainers Postgres is shared, and a
 * trigger left on {@code ad_campaigns} would break every other class that deletes a campaign.
 * {@code @ResourceLock("integration-db")} on {@link BaseIntegrationTest} serialises integration
 * classes, so the bracket holds.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CampaignDeleteTriggerIntegrationTest extends BaseIntegrationTest {

    /**
     * Kept in sync with V20260831_001 by hand — nothing enforces it. That is why the migration
     * is also verified by a real Flyway run against the dev DB: the test schema is generated
     * from JPA entities, so a migration is invisible to it either way.
     */
    private static final String CREATE_FUNCTION = """
            CREATE OR REPLACE FUNCTION prevent_active_campaign_delete() RETURNS TRIGGER AS
            $$
            BEGIN
                IF EXISTS (SELECT 1
                           FROM business
                           WHERE active_campaign_id = OLD.campaign_id) THEN
                    RAISE EXCEPTION 'Cannot delete campaign %: it is the active campaign for a business', OLD.campaign_id
                        USING ERRCODE = '23514';
                END IF;
                RETURN OLD;
            END;
            $$ LANGUAGE plpgsql
            """;

    private static final String DROP_TRIGGER =
            "DROP TRIGGER IF EXISTS trg_prevent_active_campaign_delete ON ad_campaigns";

    private static final String CREATE_TRIGGER = """
            CREATE TRIGGER trg_prevent_active_campaign_delete
                BEFORE DELETE ON ad_campaigns
                FOR EACH ROW
                EXECUTE FUNCTION prevent_active_campaign_delete()
            """;

    private static final String BUNDLE_ID = "bundle-id-1";
    private static final String BUSINESS_ID = "business-id-1";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AdCampaignRepository adCampaignRepository;

    @Autowired
    private BundleSubscriptionRepository subscriptionRepository;

    @Autowired
    private BusinessRepository businessRepository;

    @BeforeAll
    void installTrigger() {
        jdbcTemplate.execute(CREATE_FUNCTION);
        jdbcTemplate.execute(DROP_TRIGGER);
        jdbcTemplate.execute(CREATE_TRIGGER);
    }

    @AfterAll
    void removeTrigger() {
        jdbcTemplate.execute(DROP_TRIGGER);
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS prevent_active_campaign_delete()");
    }

    @BeforeEach
    @AfterEach
    void resetRows() {
        // Businesses before campaigns: the installed trigger blocks deleting a campaign that is
        // still a business's active_campaign_id, so clearing campaigns first would throw.
        subscriptionRepository.deleteAll();
        businessRepository.deleteAll();
        adCampaignRepository.deleteAll();
    }

    private AdCampaign newCampaign() {
        AdCampaign campaign = new AdCampaign();
        campaign.setCampaignId(new AdCampaignIdentifier());
        campaign.setBusinessId(new BusinessIdentifier(BUSINESS_ID));
        campaign.setName("Summer campaign");
        return adCampaignRepository.save(campaign);
    }

    private void givenBusinessWithActiveCampaign(AdCampaign campaign) {
        Business business = new Business();
        business.setBusinessId(new BusinessIdentifier(BUSINESS_ID));
        business.setName("Acme");
        business.setActiveCampaignId(campaign == null ? null : campaign.getCampaignId().getCampaignId());
        businessRepository.save(business);
    }

    private void givenSubscription(BundleSubscriptionStatus status) {
        BundleSubscription subscription = new BundleSubscription();
        subscription.setSubscriptionId(UUID.randomUUID().toString());
        subscription.setBundleId(BUNDLE_ID);
        subscription.setAdvertiserBusinessId(BUSINESS_ID);
        subscription.setStripeCheckoutSessionId("cs_test_" + UUID.randomUUID());
        subscription.setStatus(status);
        subscription.setMonthlyAmount(new BigDecimal("120.00"));
        subscription.setScreenCount(30);
        subscriptionRepository.save(subscription);
    }

    @Test
    void deletingACampaignThatIsABusinessActiveCampaign_isBlocked() {
        AdCampaign campaign = newCampaign();
        givenBusinessWithActiveCampaign(campaign);

        assertThrows(DataIntegrityViolationException.class,
                () -> adCampaignRepository.delete(campaign));

        assertTrue(adCampaignRepository.findById(campaign.getId()).isPresent());
    }

    @Test
    void deletingACampaignThatIsNoBusinessActiveCampaign_succeeds() {
        AdCampaign campaign = newCampaign();
        // a different campaign is this business's active one
        givenBusinessWithActiveCampaign(newCampaign());

        assertDoesNotThrow(() -> adCampaignRepository.delete(campaign));

        assertTrue(adCampaignRepository.findById(campaign.getId()).isEmpty());
    }

    /**
     * P6 follow-up, behaviour change 2: subscription state no longer blocks a campaign delete.
     * The old guard also raised for a live {@code bundle_subscriptions.campaign_id}; with that
     * column dropped, only "is a business's active campaign" remains. A campaign that is not any
     * business's active pointer is deletable even with subscriptions on the books.
     */
    @Test
    void deletingACampaignWithSubscriptionsButNoActivePointer_succeeds() {
        AdCampaign campaign = newCampaign();
        givenBusinessWithActiveCampaign(null); // business exists, no active campaign
        givenSubscription(BundleSubscriptionStatus.ACTIVE);
        givenSubscription(BundleSubscriptionStatus.CANCELED);

        assertDoesNotThrow(() -> adCampaignRepository.delete(campaign));

        assertTrue(adCampaignRepository.findById(campaign.getId()).isEmpty());
    }

    @Test
    void deletingACampaignWithNoBusinessRow_succeeds() {
        AdCampaign campaign = newCampaign();

        assertDoesNotThrow(() -> adCampaignRepository.delete(campaign));

        assertTrue(adCampaignRepository.findById(campaign.getId()).isEmpty());
    }
}
