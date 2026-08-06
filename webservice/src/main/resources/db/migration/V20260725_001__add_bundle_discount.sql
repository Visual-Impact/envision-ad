-- Admin-set percentage discount per bundle (P2's "bundle discounts", pulled into P1).
--
-- The discount is applied by BundleDiscountModifier inside the pricing pipeline, so
-- it lands on bundle_subscriptions.monthly_amount (what the advertiser is charged)
-- while bundle_subscription_items keep each screen's FULL media.price. That split is
-- deliberate: the platform absorbs the discount out of its own fee, and media owners
-- are paid as if there were no promotion. M5's payout job must not scale item amounts.
--
-- Consequence to keep in mind: a discount larger than stripe.platform-fee-percent
-- means paying owners more than the subscription collects. Not blocked at the DB
-- level (the fee is configurable and a loss-leader may be intentional); the admin
-- form warns past that threshold.

ALTER TABLE bundles
    ADD COLUMN discount_percent INTEGER NOT NULL DEFAULT 0;

ALTER TABLE bundles
    ADD CONSTRAINT chk_bundles_discount_percent
        CHECK (discount_percent >= 0 AND discount_percent <= 100);

COMMENT ON COLUMN bundles.discount_percent IS 'Whole-percent discount applied to this bundle''s summed screen price. 0 = no discount. Absorbed by the platform fee, not by media-owner payouts.';
