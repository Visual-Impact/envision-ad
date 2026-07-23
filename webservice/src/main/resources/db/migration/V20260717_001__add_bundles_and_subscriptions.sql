-- P1 (Bundles & Monthly Subscriptions) — Milestone 1: additive data model.
--
-- Scope note: this migration is deliberately ADDITIVE ONLY. The brief's Data Model
-- section pairs these tables with dropping `reservations`/`payment_intents` and
-- stripping the reservations clause out of prevent_active_campaign_delete(). Both
-- are deferred to P1-M6 (legacy retirement), because the Reservation/PaymentIntent
-- entities and the whole reservation/ module still map those tables until then —
-- dropping now would break the running app (ddl-auto: none) across M2–M5, and P9
-- (proof-of-display) still reads reservations. M6 gets its own migration for the
-- drops and re-replaces the trigger function with the bundle-only version.

CREATE TABLE bundles
(
    id             BIGSERIAL PRIMARY KEY,
    bundle_id      VARCHAR(36) UNIQUE NOT NULL,
    name_en        VARCHAR(255)       NOT NULL,
    name_fr        VARCHAR(255)       NOT NULL,
    description_en TEXT,
    description_fr TEXT,
    ideal_for_en   VARCHAR(500),
    ideal_for_fr   VARCHAR(500),
    badge_color    VARCHAR(7)         NOT NULL,
    rule_type      VARCHAR(20)        NOT NULL,
    rule_value     VARCHAR(255),
    active         BOOLEAN            NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP                   DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP                   DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_bundles_rule_value CHECK (
        (rule_type = 'FULL_NETWORK' AND rule_value IS NULL)
            OR (rule_type <> 'FULL_NETWORK' AND rule_value IS NOT NULL)
        )
);

-- Admin-managed manual exclusions: a media matches the bundle's rule but the
-- admin removed it from that specific bundle.
CREATE TABLE bundle_excluded_medias
(
    bundle_id VARCHAR(36) NOT NULL REFERENCES bundles (bundle_id) ON DELETE CASCADE,
    media_id  UUID        NOT NULL REFERENCES media (media_id) ON DELETE CASCADE,

    PRIMARY KEY (bundle_id, media_id)
);

-- Stripe Customer objects for advertisers. Distinct from stripe_accounts, which
-- holds Connect accounts for media OWNERS.
CREATE TABLE stripe_customers
(
    id                 BIGSERIAL PRIMARY KEY,
    business_id        VARCHAR(36) UNIQUE  NOT NULL,
    stripe_customer_id VARCHAR(255) UNIQUE NOT NULL,
    created_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_business_stripe_customer
        FOREIGN KEY (business_id) REFERENCES business (business_id) ON DELETE CASCADE
);

-- The new source of truth for "who advertises where."
CREATE TABLE bundle_subscriptions
(
    id                         BIGSERIAL PRIMARY KEY,
    subscription_id            VARCHAR(36) UNIQUE  NOT NULL,
    bundle_id                  VARCHAR(36)         NOT NULL REFERENCES bundles (bundle_id),
    advertiser_business_id     VARCHAR(36)         NOT NULL REFERENCES business (business_id),
    -- The active campaign hook for P6. RESTRICT rather than SET NULL because the
    -- column is NOT NULL; the campaign-delete trigger below is the friendlier guard.
    campaign_id                VARCHAR(36)         NOT NULL REFERENCES ad_campaigns (campaign_id) ON DELETE RESTRICT,
    -- NULL until checkout.session.completed lands.
    stripe_subscription_id     VARCHAR(255) UNIQUE,
    stripe_checkout_session_id VARCHAR(255) UNIQUE NOT NULL,
    status                     VARCHAR(20)         NOT NULL,
    -- Locked total at creation (post-eligibility, pre-coupon).
    monthly_amount             DECIMAL(10, 2)      NOT NULL,
    -- Locked eligible screen count at creation.
    screen_count               INTEGER             NOT NULL,
    current_period_end         TIMESTAMP,
    cancel_at_period_end       BOOLEAN             NOT NULL DEFAULT FALSE,
    canceled_at                TIMESTAMP,
    created_at                 TIMESTAMP                    DEFAULT CURRENT_TIMESTAMP
);

-- The locked per-screen breakdown captured at subscription creation, so the monthly
-- payout job never has to recompute eligibility (which drifts as media change).
CREATE TABLE bundle_subscription_items
(
    id                      BIGSERIAL PRIMARY KEY,
    subscription_id         VARCHAR(36)    NOT NULL REFERENCES bundle_subscriptions (subscription_id) ON DELETE CASCADE,
    media_id                UUID           NOT NULL REFERENCES media (media_id),
    -- Denormalized on purpose (no FK): payouts must reflect the owner AT SIGNUP,
    -- even if the media changes hands later.
    media_owner_business_id VARCHAR(36)    NOT NULL,
    -- This screen's media.price at lock time.
    monthly_amount          DECIMAL(10, 2) NOT NULL
);

-- A business may hold at most one live subscription per bundle. Enforced in the
-- database, not just the service layer; CANCELED rows are exempt so a business can
-- resubscribe to a bundle it previously left.
CREATE UNIQUE INDEX uq_bundle_subscriptions_active_per_business
    ON bundle_subscriptions (bundle_id, advertiser_business_id)
    WHERE status IN ('INCOMPLETE', 'ACTIVE', 'PAST_DUE');

-- Free-text region, entered the same way city/province already are. Bundle REGION
-- rules match rule_value against this case-insensitively, exactly like CITY does
-- against media_location.city. No taxonomy table by design (see brief).
ALTER TABLE media_location
    ADD COLUMN region VARCHAR(100);

-- media.price is reinterpreted from "price per week" to "monthly price per screen".
-- No column rename (avoids touching every read site). Existing rows are all test
-- data (client confirmed no real users), so they are reset to the new default.
ALTER TABLE media
    ALTER COLUMN price SET DEFAULT 4.00;

UPDATE media
SET price = 4.00;

COMMENT ON COLUMN media.price IS 'Monthly price per screen (CAD), billed via Stripe Billing subscriptions. Superseded weekly-reservation pricing.';

-- Extend the guard from V20260714_001 to also protect a campaign that is the active
-- campaign of a live bundle subscription. The reservations clause stays until M6
-- retires that table. The trigger itself already exists and points at this function
-- by name, so only the function is replaced.
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
$$ LANGUAGE plpgsql;
