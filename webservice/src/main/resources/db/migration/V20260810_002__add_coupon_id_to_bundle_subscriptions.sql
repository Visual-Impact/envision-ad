-- Records which coupon (if any) was applied at subscribe time, independent of Stripe
-- being the source of truth for the discount itself (brief §3.2, P3 M2). Nullable and
-- frozen at creation: renewal-month application/expiry is Stripe's own
-- Coupon.duration doing its job automatically, never a re-attach on our side.
--
-- No ON DELETE behavior specified beyond the default (NO ACTION/RESTRICT) — matching
-- P1's own deliberate choice for bundle_subscriptions' other FKs (see V20260717_001 and
-- decision D47): a coupon is never deleted, only archived, so this FK is never actually
-- tested against a delete in practice.
ALTER TABLE bundle_subscriptions
    ADD COLUMN coupon_id BIGINT REFERENCES coupons (id);

CREATE INDEX idx_bundle_subscriptions_coupon ON bundle_subscriptions (coupon_id) WHERE coupon_id IS NOT NULL;

COMMENT ON COLUMN bundle_subscriptions.coupon_id IS 'Coupon applied at subscribe time, if any. Set once, never changes afterward — see P3-PROGRESS.md.';
