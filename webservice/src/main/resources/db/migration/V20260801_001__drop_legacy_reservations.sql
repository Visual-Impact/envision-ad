-- P1 M6 — legacy retirement. This is the deferred half of decision D1.
--
-- WHY THIS IS ONLY HAPPENING NOW: M1 created the bundle tables but deliberately kept
-- `reservations` and `payment_intents`. Under `ddl-auto: none` (local/prod) the running
-- app maps both tables through the reservation/ module and PaymentIntent entity, so
-- dropping them in M1 would have broken every environment across M2–M5. Under
-- `ddl-auto: create` (tests) Hibernate simply recreates whatever still has an entity,
-- so the drop would not even have been observable. M6 deletes those entities in the
-- same commit as this migration, which is what finally makes the drop safe and real.
--
-- The client confirmed (2026-07-17) the weekly-reservation flow was never used by real
-- users, so there is no history worth preserving and no data migration to perform.
-- Bundle subscriptions are now the sole source of truth for "who advertises where".

-- Strip the reservations clause out of the campaign-delete guard, leaving only the
-- bundle-subscription clause. M1's V20260717_001 deliberately shipped the two-clause
-- version so its trigger test had both branches to exercise; with `reservations` gone
-- the first branch would reference a missing table and every campaign delete would
-- fail with `relation "reservations" does not exist`.
--
-- The application layer mirrors this in AdCampaignServiceImpl#campaignIsTiedToSubscription
-- and returns a friendly 409 first; this trigger is the backstop for direct/manual
-- database changes that bypass the app, matching the intent of the original
-- V20260714_001. CREATE OR REPLACE re-declares the function in place — the trigger
-- trg_prevent_active_campaign_delete already points at it and needs no re-creation.
CREATE OR REPLACE FUNCTION prevent_active_campaign_delete() RETURNS TRIGGER AS
$$
BEGIN
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

-- Neither table is referenced by a foreign key from anywhere else: payment_intents
-- holds `reservation_id` as a plain VARCHAR column, never a constraint (see
-- V20260525_001), and nothing points at payment_intents at all. So no CASCADE is
-- needed and the order below is a readability choice, not a dependency one.
DROP TABLE IF EXISTS payment_intents;
DROP TABLE IF EXISTS reservations;
