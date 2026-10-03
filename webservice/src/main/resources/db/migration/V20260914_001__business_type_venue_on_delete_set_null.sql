-- P4 (business types & competitive exclusion). V20260814_001 added
-- business.business_type_venue_id with a bare REFERENCES venue (venue_id), so deleting a venue
-- that any business uses as its business type fails with an FK violation. Brief 04 specifies
-- ON DELETE SET NULL, mirroring media.venue_id: removing a venue from the taxonomy reverts those
-- businesses to "no business type", i.e. no exclusions.
--
-- The constraint name is Postgres's default for V20260814_001's unnamed inline FK, read from
-- pg_constraint on the dev database rather than guessed.
ALTER TABLE business
    DROP CONSTRAINT business_business_type_venue_id_fkey,
    ADD CONSTRAINT business_business_type_venue_id_fkey
        FOREIGN KEY (business_type_venue_id) REFERENCES venue (venue_id) ON DELETE SET NULL;
