-- P5 (admin-only account creation): two additive, backward-compatible columns on
-- business. See docs/platform-improvements/briefs/05-admin-only-accounts.md "Data Model".
--
-- business_type_venue_id is a hook for P4's competitive-exclusion logic — this brief
-- only stores it (via the admin create-account form's venue-taxonomy dropdown), P4 owns
-- reading it. References venue(venue_id), the existing external unique identifier column
-- (VARCHAR(36)), NOT venue(id) (the SERIAL surrogate PK) — matching the precedent already
-- set by media.venue_id in V20260525_002__add_venue_tags.sql. Nullable = "no business type
-- set" (P5-PROGRESS.md ground-truth item 3 — the brief's own prose says "venues(venue_id)",
-- which doesn't match the real (singular) table name).
--
-- active drives the admin deactivate/reactivate action (brief FR 5.5), independent of the
-- Auth0-side `blocked` flag. Existing rows backfill to true via the DEFAULT.
ALTER TABLE business
    ADD COLUMN business_type_venue_id VARCHAR(36) REFERENCES venue (venue_id),
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN business.business_type_venue_id IS 'Hook for P4 business-type exclusion logic; nullable = no business type set. FK to venue(venue_id) (the external unique id), not venue.id (the surrogate PK).';
COMMENT ON COLUMN business.active IS 'Drives admin deactivate/reactivate (P5 FR 5.5); independent of the Auth0-side blocked flag mirrored via AdminAccountService.';
