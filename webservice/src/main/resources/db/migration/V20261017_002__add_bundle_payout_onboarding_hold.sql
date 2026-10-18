-- Holds a media owner's share when their Stripe Connect onboarding is incomplete at the time
-- an invoice is paid, instead of skipping it for good.
--
-- WHY: Terms of Service §7 (media owners, payouts) promises that the share is held and
-- transferred once onboarding completes, provided it completes within 90 days after the
-- invoice is paid; after that the amount is no longer owed. Until now such an owner got a
-- terminal SKIPPED_NOT_ONBOARDED row and was never paid.
--
-- New statuses (status is VARCHAR(30) with no CHECK constraint, so no DDL is needed for them):
--   HELD_NOT_ONBOARDED    the share is owed and waiting for onboarding to complete. The row
--                         keeps the amount and funding source (stripe_source_charge_id) that
--                         were decided when the invoice was paid, so the eventual transfer
--                         sends exactly those. Released to PAID (or FAILED) when onboarding
--                         completes, by the account.updated webhook, the onboarding status
--                         read, or BundlePayoutHoldJob.
--   EXPIRED_NOT_ONBOARDED terminal: onboarding was still incomplete 90 days after the row was
--                         created (created_at is never earlier than the payment, so this errs
--                         in the owner's favour).
--
-- Existing SKIPPED_NOT_ONBOARDED rows are deliberately left as they are: they predate the
-- clause and are settled by hand. The status is no longer written.
--
-- The unique index on (stripe_invoice_id, media_owner_business_id) from V20260726_001 is
-- unchanged: a held row still counts as "handled", so a redelivered invoice.paid never adds a
-- second row for the same owner.

-- When the hold ended: the time of the release attempt, or of the expiry. NULL for a row that
-- was never held. BundlePayoutRetryJob measures its 30-day window from this rather than from
-- created_at for a released row, so a share held for weeks and then short of funds on release
-- is still retried.
ALTER TABLE bundle_payouts ADD COLUMN hold_ended_at TIMESTAMP;

-- Releasing on onboarding looks up one owner's held rows; the hourly sweep reads all of them.
-- Partial, so it only ever contains rows still waiting.
CREATE INDEX idx_bundle_payouts_held
    ON bundle_payouts (media_owner_business_id, created_at)
    WHERE status = 'HELD_NOT_ONBOARDED';

COMMENT ON COLUMN bundle_payouts.status IS 'PAID, HELD_NOT_ONBOARDED (owed, waiting for Connect onboarding, up to 90 days), EXPIRED_NOT_ONBOARDED, SKIPPED_NOT_ONBOARDED (legacy, no longer written), SKIPPED_ZERO_AMOUNT or FAILED.';
COMMENT ON COLUMN bundle_payouts.hold_ended_at IS 'When a HELD_NOT_ONBOARDED row was released or expired; NULL if it was never held.';
