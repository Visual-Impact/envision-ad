package com.envisionad.webservice.advertisement.dataaccesslayer;

import com.envisionad.webservice.business.dataaccesslayer.BusinessIdentifier;
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
 * Behaviour of the {@code prevent_active_campaign_delete()} guard as amended by
 * V20260717_001: a campaign that is the active campaign of a live bundle subscription
 * cannot be deleted.
 *
 * <p>The function and trigger are plpgsql and so are absent from the entity-generated
 * test schema; this class installs them, mirroring the migration, and removes them
 * afterwards. Dropping in {@code @AfterAll} is not optional — the Testcontainers
 * Postgres is shared, and a trigger left on {@code ad_campaigns} would break every
 * other class that deletes a campaign. {@code @ResourceLock("integration-db")} on
 * {@link BaseIntegrationTest} serialises integration classes, so the bracket holds.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CampaignDeleteTriggerIntegrationTest extends BaseIntegrationTest {

    /**
     * Kept in sync with V20260717_001. Executed as one whole statement — the $$-quoted
     * body contains semicolons, so splitting the migration file on ';' would produce
     * broken fragments.
     */
    private static final String CREATE_FUNCTION = """
            CREATE OR REPLACE FUNCTION prevent_active_campaign_delete() RETURNS TRIGGER AS
            $$
            BEGIN
                IF EXISTS (SELECT 1
                           FROM reservations
                           WHERE campaign_id = OLD.campaign_id
                             AND status IN ('CONFIRMED', 'APPROVED', 'PENDING')
                             AND end_date >= NOW()) THEN
                    RAISE EXCEPTION 'Cannot delete campaign %: it is tied to an active reservation', OLD.campaign_id
                        USING ERRCODE = '23514';
                END IF;
                IF EXISTS (SELECT 1
                           FROM bundle_subscriptions
                           WHERE campaign_id = OLD.campaign_id
                             AND status IN ('ACTIVE', 'PAST_DUE')) THEN
                    RAISE EXCEPTION 'Cannot delete campaign %: it is the active campaign of a subscription', OLD.campaign_id
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
    void setUp() {
        subscriptionRepository.deleteAll();
        adCampaignRepository.deleteAll();
    }

    private AdCampaign newCampaign() {
        AdCampaign campaign = new AdCampaign();
        campaign.setCampaignId(new AdCampaignIdentifier());
        campaign.setBusinessId(new BusinessIdentifier(BUSINESS_ID));
        campaign.setName("Summer campaign");
        return adCampaignRepository.save(campaign);
    }

    private void givenSubscription(AdCampaign campaign, BundleSubscriptionStatus status) {
        BundleSubscription subscription = new BundleSubscription();
        subscription.setSubscriptionId(UUID.randomUUID().toString());
        subscription.setBundleId(BUNDLE_ID);
        subscription.setAdvertiserBusinessId(BUSINESS_ID);
        subscription.setCampaignId(campaign.getCampaignId().getCampaignId());
        subscription.setStripeCheckoutSessionId("cs_test_" + UUID.randomUUID());
        subscription.setStatus(status);
        subscription.setMonthlyAmount(new BigDecimal("120.00"));
        subscription.setScreenCount(30);
        subscriptionRepository.save(subscription);
    }

    @Test
    void deletingCampaignWithActiveSubscription_isBlocked() {
        AdCampaign campaign = newCampaign();
        givenSubscription(campaign, BundleSubscriptionStatus.ACTIVE);

        assertThrows(DataIntegrityViolationException.class,
                () -> adCampaignRepository.delete(campaign));

        assertTrue(adCampaignRepository.findById(campaign.getId()).isPresent());
    }

    @Test
    void deletingCampaignWithPastDueSubscription_isBlocked() {
        AdCampaign campaign = newCampaign();
        givenSubscription(campaign, BundleSubscriptionStatus.PAST_DUE);

        assertThrows(DataIntegrityViolationException.class,
                () -> adCampaignRepository.delete(campaign));

        assertTrue(adCampaignRepository.findById(campaign.getId()).isPresent());
    }

    @Test
    void deletingCampaignWithNoSubscription_succeeds() {
        AdCampaign campaign = newCampaign();

        assertDoesNotThrow(() -> adCampaignRepository.delete(campaign));

        assertTrue(adCampaignRepository.findById(campaign.getId()).isEmpty());
    }

    @Test
    void deletingCampaignReferencedOnlyByAnotherCampaignsSubscription_succeeds() {
        AdCampaign blocked = newCampaign();
        AdCampaign unrelated = newCampaign();
        givenSubscription(blocked, BundleSubscriptionStatus.ACTIVE);

        assertDoesNotThrow(() -> adCampaignRepository.delete(unrelated));

        assertTrue(adCampaignRepository.findById(unrelated.getId()).isEmpty());
        assertTrue(adCampaignRepository.findById(blocked.getId()).isPresent());
    }

    // Deliberately NOT asserted: "a campaign whose only subscription is CANCELED can be
    // deleted." The trigger allows it, but in a Flyway-built database
    // bundle_subscriptions.campaign_id is NOT NULL with ON DELETE RESTRICT, which blocks
    // the delete regardless of status. The test schema has no foreign keys, so asserting
    // it here would encode a contract that production does not honour.
}
