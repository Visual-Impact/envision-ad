-- P6 follow-up — single-source the active campaign (brief 06, Open Question 5, option c2).
--
-- P1 stamped bundle_subscriptions.campaign_id once at checkout and never updated it.
-- P6 M1 added business.active_campaign_id as the real per-advertiser pointer. Keeping
-- both means two places claim "the advertiser's active campaign", and the moment M2
-- ships a swap the subscription column goes stale on every swapped-away campaign.
-- This migration drops the subscription column; business.active_campaign_id becomes
-- the single source of truth and every reader derives from it.
--
-- NOT covered by the test suite: it runs flyway.enabled=false + ddl-auto=create, so
-- the schema comes from JPA entities and this SQL never executes there. Verified by
-- hand against the real dev DB in a rolled-back transaction (guard SELECT, column +
-- FK drop, trigger body).

-- (1) Guard: refuse to drop the column while any business would lose its only campaign
-- reference. M1's backfill left active_campaign_id NULL for businesses whose live
-- subscriptions disagreed on a campaign; for those the per-row campaign_id is the only
-- thing proof-of-display and the new-subscription email can read, and dropping it is a
-- live regression (media owners can't submit proof, notifications skip). Dev has zero
-- such businesses (checked 2026-08-31, lead signed off on guard-only); staging/prod are
-- unproven, so this fails the migration loudly rather than silently regressing them.
DO $$
DECLARE
    affected BIGINT;
BEGIN
    SELECT COUNT(*) INTO affected
    FROM (
        SELECT b.business_id
        FROM business b
        JOIN bundle_subscriptions s ON s.advertiser_business_id = b.business_id
        WHERE b.active_campaign_id IS NULL
          AND s.status IN ('ACTIVE', 'PAST_DUE')
        GROUP BY b.business_id
        HAVING COUNT(DISTINCT s.campaign_id) >= 1
    ) offenders;

    IF affected > 0 THEN
        RAISE EXCEPTION
            'Cannot drop bundle_subscriptions.campaign_id: % business(es) have a live '
            'subscription but no active_campaign_id. Set business.active_campaign_id for '
            'them first (e.g. MIN(campaign_id) among their live subscriptions), then re-run.',
            affected;
    END IF;
END $$;

-- (2) Drop the column. The FK bundle_subscriptions_campaign_id_fkey goes with it; the
-- explicit DROP CONSTRAINT is redundant but kept for readability and guarded so order
-- can't break the migration.
ALTER TABLE bundle_subscriptions
    DROP CONSTRAINT IF EXISTS bundle_subscriptions_campaign_id_fkey;

ALTER TABLE bundle_subscriptions
    DROP COLUMN campaign_id;

-- (3) Simplify prevent_active_campaign_delete(). M1's V20260822_001 gave it two clauses:
--   1. campaign is any business's active_campaign_id  (unconditional, fires first)
--   2. campaign is a live bundle_subscriptions.campaign_id
-- With campaign_id off the subscription, clause 2 can no longer be expressed, and it was
-- already a strict subset of clause 1 anyway (every business with a live subscription has
-- active_campaign_id set — enforced by checkout and by this migration's guard). The
-- function reduces to clause 1. Same function name, so trg_prevent_active_campaign_delete
-- keeps working with no re-creation.
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
$$ LANGUAGE plpgsql;
