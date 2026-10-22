-- Lets a FAILED payout be retried when Stripe rejected it only for insufficient available
-- funds, and makes that retry send exactly what the first attempt sent.
--
-- WHY: in live mode a card payment sits in the platform's PENDING balance for days. A
-- transfer funded from the AVAILABLE balance on invoice.paid therefore fails with
-- balance_insufficient, and before this change the FAILED row was terminal (see
-- V20260726_001). Transfers are now tied to the invoice's charge with source_transaction
-- wherever the payouts fit inside it, which Stripe allows while the charge is still
-- pending; the balance-funded path remains for the case where a bundle discount exceeds
-- the platform fee and owners are owed more than the invoice collected. Those, and any
-- other insufficient-funds failure, are retried by a scheduled job.
--
-- This reverses the "FAILED is terminal, no retry job" rule recorded in V20260726_001 for
-- the insufficient-funds case only. Every other failure stays terminal.

-- Stripe's error code (e.g. balance_insufficient) when status = FAILED. The retry job
-- selects on this, never on the free-text failure_reason.
ALTER TABLE bundle_payouts ADD COLUMN failure_code VARCHAR(64);

-- The charge the transfer was funded from via source_transaction, or NULL when it was
-- funded from the platform's available balance. Stored so a retry repeats the first
-- attempt's parameters exactly: Stripe rejects a reused idempotency key sent with
-- different parameters.
ALTER TABLE bundle_payouts ADD COLUMN stripe_source_charge_id VARCHAR(255);

-- When a transfer was last attempted for this row; NULL for a skipped owner, where none
-- was. The retry job waits at least 24h between attempts, because Stripe replays the
-- cached error for a reused idempotency key until it expires. Existing rows were attempted
-- once, at insert.
ALTER TABLE bundle_payouts ADD COLUMN last_attempt_at TIMESTAMP;
UPDATE bundle_payouts SET last_attempt_at = created_at WHERE status IN ('PAID', 'FAILED');

-- The retry job's query: FAILED rows with a given failure code. Partial, so it stays tiny.
CREATE INDEX idx_bundle_payouts_retryable
    ON bundle_payouts (failure_code, last_attempt_at)
    WHERE status = 'FAILED';

COMMENT ON COLUMN bundle_payouts.failure_code IS 'Stripe error code when status = FAILED. balance_insufficient rows are retried by BundlePayoutRetryJob.';
COMMENT ON COLUMN bundle_payouts.stripe_source_charge_id IS 'Charge passed as source_transaction, or NULL when funded from the platform''s available balance.';
COMMENT ON COLUMN bundle_payouts.last_attempt_at IS 'Time of the most recent transfer attempt for this row.';
