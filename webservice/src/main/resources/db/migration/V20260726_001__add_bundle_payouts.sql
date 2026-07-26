-- Durable record of every media-owner payout attempted for a paid invoice (M5).
--
-- WHY THIS TABLE EXISTS: double-payment protection. Stripe redelivers webhook events
-- as a matter of course, and M5's invoice.paid handler deliberately throws on a
-- lookup miss to force redelivery (brief req. 16), so redelivery is not an edge case
-- here — it is the designed happy path for the ordering hazard. Stripe's own
-- idempotency keys cannot cover this: they are retained ~24h while Stripe's retry
-- schedule runs ~3 days. This ledger is the authoritative "already paid" record.
--
-- The unique index on (stripe_invoice_id, media_owner_business_id) is the guard: one
-- payout row per owner per invoice, forever. A monthly renewal is a *different*
-- invoice, so it pays out normally.

CREATE TABLE bundle_payouts
(
    id                      BIGSERIAL PRIMARY KEY,
    -- Stripe's invoice id. The payout unit is the invoice, not the subscription:
    -- each monthly renewal is its own invoice and its own set of transfers.
    stripe_invoice_id       VARCHAR(255)   NOT NULL,
    -- Our local bundle_subscriptions.subscription_id. Deliberately NOT a foreign key,
    -- matching the denormalization already used by bundle_subscription_items: a
    -- bundle delete cascades away its subscriptions (brief req. 5), and a financial
    -- record must outlive that rather than silently vanish with it.
    subscription_id         VARCHAR(36)    NOT NULL,
    media_owner_business_id VARCHAR(36)    NOT NULL,
    -- The owner's share BEFORE the platform fee: the sum of that owner's
    -- bundle_subscription_items.monthly_amount, which hold each screen's FULL
    -- undiscounted media.price. Never scaled by the bundle discount — the platform
    -- absorbs discounts, not media owners (see V20260725_001 and decision D30).
    gross_amount            DECIMAL(10, 2) NOT NULL,
    -- What was actually transferred: gross_amount minus stripe.platform-fee-percent.
    -- Stored rather than derived because the fee percentage is configurable and can
    -- change between cycles, which would silently rewrite history.
    amount                  DECIMAL(10, 2) NOT NULL,
    -- Null unless status = PAID.
    stripe_transfer_id      VARCHAR(255),
    -- PAID, SKIPPED_NOT_ONBOARDED (owner has no stripe_accounts row, or has not
    -- finished Connect onboarding), FAILED (Stripe rejected the transfer).
    status                  VARCHAR(30)    NOT NULL,
    -- Stripe's error message when status = FAILED, so a human can see why without
    -- digging through application logs.
    failure_reason          TEXT,
    created_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- The double-payment guard. Covers every status, not just PAID, which makes the
-- no-double-pay guarantee absolute at the cost of making SKIPPED and FAILED terminal
-- for that invoice. That is the brief's stated intent: an owner whose Connect
-- onboarding has lapsed "simply doesn't get paid for that cycle", with no
-- compensating or retry job in scope (req. 17). A human reading this table can see
-- exactly who was missed and settle up out of band.
CREATE UNIQUE INDEX uq_bundle_payouts_invoice_owner
    ON bundle_payouts (stripe_invoice_id, media_owner_business_id);

-- Answers "what did we pay out for this subscription, and when".
CREATE INDEX idx_bundle_payouts_subscription ON bundle_payouts (subscription_id);

COMMENT ON TABLE bundle_payouts IS 'One row per media owner per paid invoice. Doubles as the idempotency guard against Stripe webhook redelivery and as the payout audit trail.';
COMMENT ON COLUMN bundle_payouts.gross_amount IS 'Owner share before the platform fee: sum of that owner''s bundle_subscription_items.monthly_amount (full undiscounted screen prices).';
COMMENT ON COLUMN bundle_payouts.amount IS 'Amount actually transferred to the owner: gross_amount minus stripe.platform-fee-percent.';
