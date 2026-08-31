-- P6 M1: active campaign data model and database-level delete backstop.
--
-- The active pointer belongs to the advertiser business: one campaign is displayed
-- across every screen in that business's live bundle subscriptions. It stays nullable
-- because businesses with no live subscription do not need a selection.
ALTER TABLE business
    ADD COLUMN active_campaign_id VARCHAR(36) NULL
        REFERENCES ad_campaigns (campaign_id) ON DELETE SET NULL;

COMMENT ON COLUMN business.active_campaign_id IS
    'The single campaign currently displayed across this advertiser business''s live bundle subscriptions.';

-- Existing subscriptions predate the business-level pointer. Backfill only businesses
-- whose ACTIVE/PAST_DUE rows all agree on one campaign. Ambiguous businesses deliberately
-- remain NULL so the dashboard exposes the inconsistency instead of choosing arbitrarily.
WITH unambiguous_active_campaigns AS (
    SELECT advertiser_business_id,
           MIN(campaign_id) AS campaign_id
    FROM bundle_subscriptions
    WHERE status IN ('ACTIVE', 'PAST_DUE')
    GROUP BY advertiser_business_id
    HAVING COUNT(DISTINCT campaign_id) = 1
)
UPDATE business b
SET active_campaign_id = active.campaign_id
FROM unambiguous_active_campaigns active
WHERE b.business_id = active.advertiser_business_id
  AND b.active_campaign_id IS NULL;

-- Archived campaigns remain available to historical subscription/proof records but are
-- hidden from the advertiser's default list. Creative changes use a timestamp rather than
-- a boolean so M2 can derive pending notification state and enforce its quiet period.
ALTER TABLE ad_campaigns
    ADD COLUMN archived_at TIMESTAMP NULL,
    ADD COLUMN creatives_updated_at TIMESTAMP NULL;

CREATE INDEX idx_ad_campaigns_creatives_updated_at
    ON ad_campaigns (creatives_updated_at);

CREATE TABLE campaign_swap_events
(
    id                    BIGSERIAL PRIMARY KEY,
    business_id           VARCHAR(36) NOT NULL REFERENCES business (business_id) ON DELETE CASCADE,
    from_campaign_id      VARCHAR(36) NULL REFERENCES ad_campaigns (campaign_id) ON DELETE SET NULL,
    to_campaign_id        VARCHAR(36) NOT NULL REFERENCES ad_campaigns (campaign_id) ON DELETE CASCADE,
    event_type            VARCHAR(20) NOT NULL,
    triggered_by_user_id  VARCHAR(64) NULL,
    triggered_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    recipients_notified   INTEGER NOT NULL DEFAULT 0,
    recipients_failed     INTEGER NOT NULL DEFAULT 0,

    CONSTRAINT chk_campaign_swap_event_type CHECK (
        event_type IN ('INITIAL_SELECTION', 'SWAP', 'MANUAL_NOTIFY', 'AUTO_NOTIFY')
    ),
    CONSTRAINT chk_campaign_swap_recipient_counts CHECK (
        recipients_notified >= 0 AND recipients_failed >= 0
    )
);

CREATE INDEX idx_campaign_swap_events_business_triggered_at
    ON campaign_swap_events (business_id, triggered_at DESC);

CREATE INDEX idx_campaign_swap_events_campaign_triggered_at
    ON campaign_swap_events (to_campaign_id, triggered_at DESC);

-- Extend P1's existing function in place rather than adding a second BEFORE DELETE
-- trigger. The application returns the friendly domain error; this protects direct SQL.
CREATE OR REPLACE FUNCTION prevent_active_campaign_delete() RETURNS TRIGGER AS
$$
BEGIN
    IF EXISTS (SELECT 1
               FROM business
               WHERE active_campaign_id = OLD.campaign_id) THEN
        RAISE EXCEPTION 'Cannot delete campaign %: it is the active campaign for a business', OLD.campaign_id
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
$$ LANGUAGE plpgsql;
