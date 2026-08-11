-- Stripe-backed coupon codes (P3). Local mirror of a Stripe Coupon + PromotionCode
-- pair, kept just complete enough to power an admin CRUD UI and a fast client-side
-- discount preview at checkout (brief §4.6.3) — Stripe remains the source of truth
-- for redemption and discount math.
--
-- WHY code is UNIQUE with no soft-delete escape hatch: brief §4.1.a/§4.4.3 requires
-- codes to be permanently reserved once created, including archived ones, so a code
-- that was ever redeemed can never be reissued to mean something different.

CREATE TABLE coupons
(
    id                       BIGSERIAL      PRIMARY KEY,
    coupon_id                VARCHAR(36)    NOT NULL UNIQUE,
    -- Always stored uppercased; case-insensitive lookups uppercase the input instead
    -- of relying on a DB-level collation.
    code                     VARCHAR(40)    NOT NULL UNIQUE,
    discount_type            VARCHAR(20)    NOT NULL,
    percent_off              NUMERIC(5, 2),
    amount_off_cents         BIGINT,
    duration                 VARCHAR(20)    NOT NULL,
    duration_in_months       INTEGER,
    expires_at               TIMESTAMP,
    max_redemptions          INTEGER,
    times_redeemed           INTEGER        NOT NULL DEFAULT 0,
    status                   VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    stripe_coupon_id         VARCHAR(255)   NOT NULL UNIQUE,
    stripe_promotion_code_id VARCHAR(255)   NOT NULL UNIQUE,
    created_by               VARCHAR(255),
    created_at                TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Exactly one of percent_off / amount_off_cents is set, matching discount_type.
    CONSTRAINT chk_coupons_discount_shape CHECK (
        (discount_type = 'PERCENT' AND percent_off IS NOT NULL AND percent_off > 0 AND percent_off <= 100
            AND amount_off_cents IS NULL)
            OR
        (discount_type = 'FIXED_AMOUNT' AND amount_off_cents IS NOT NULL AND amount_off_cents > 0
            AND percent_off IS NULL)
        ),

    -- duration_in_months is required for REPEATING (2-12, per brief §5.3 — a value of
    -- 1 is routed to ONCE at the application layer rather than ever stored) and must
    -- be absent otherwise.
    CONSTRAINT chk_coupons_duration_months CHECK (
        (duration = 'REPEATING' AND duration_in_months IS NOT NULL AND duration_in_months BETWEEN 2 AND 12)
            OR
        (duration <> 'REPEATING' AND duration_in_months IS NULL)
        )
);

-- Admin table's default view filters out ARCHIVED (brief §4.4.3).
CREATE INDEX idx_coupons_status ON coupons (status);

COMMENT ON TABLE coupons IS 'Local mirror of a Stripe Coupon + PromotionCode pair. Discount shape is immutable after creation; code is retired forever once created, never reused.';
COMMENT ON COLUMN coupons.times_redeemed IS 'Cached counter, refreshed from Stripe PromotionCode.timesRedeemed on each admin list load (brief §4.5) — not webhook-driven.';
