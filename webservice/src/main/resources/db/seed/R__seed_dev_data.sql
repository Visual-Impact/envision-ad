-- Local-dev-only sample data: venues, media locations, media screens, bundles, and one
-- real teammate's business/employee record.
--
-- Scope is otherwise deliberately supply-side only. No other business/employee rows are
-- seeded here because employee.user_id is the raw Auth0 JWT `sub` used for every
-- authorization check (see JwtUtils) — a fabricated value would just make a seeded
-- business invisible to whoever's actually logged in. Create your own business/campaign
-- through the normal signup flow in the running app instead; this migration exists to
-- give that flow something real to browse (media to select, bundles to subscribe to)
-- without requiring a second developer to have set up a media-owner account first.
--
-- Applies ONLY under the `local` Spring profile — see application-local.yml's
-- spring.flyway.locations. application.yml / application-prod.yml do not include
-- db/seed, so this never runs against production.
--
-- media.business_id and media_location.business_id are UUID columns with NO FK
-- constraint (a pre-existing schema gap, not introduced here), so they're set to a
-- fixed placeholder UUID rather than a real business — nothing enforces or requires
-- that it resolve to an actual business row.
--
-- Repeatable migration (Flyway re-runs it whenever this file's checksum changes), so
-- the whole body is guarded by a single sentinel check on the first seeded venue to
-- keep re-runs idempotent and avoid clobbering any hand-made local state.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM venue WHERE venue_id = '00000000-0000-4000-8000-000000000001') THEN

        INSERT INTO venue (venue_id, name_en, name_fr, color_code) VALUES
            ('00000000-0000-4000-8000-000000000001', 'Downtown Mall', 'Centre commercial du centre-ville', '#2563EB'),
            ('00000000-0000-4000-8000-000000000002', 'Transit Station', 'Station de transport en commun', '#059669'),
            ('00000000-0000-4000-8000-000000000003', 'Fitness Club', 'Club de conditionnement physique', '#DC2626');

        INSERT INTO media_location
            (media_location_id, name, country, province, city, region, street, postal_code, latitude, longitude, business_id)
        VALUES
            ('a0000000-0000-4000-8000-000000000001', 'Downtown Mall - Main Entrance', 'Canada', 'Ontario', 'Toronto', 'Greater Toronto Area', '100 Queen St W', 'M5H 2N2', 43.6532, -79.3832, '00000000-0000-4000-8000-0000000000ff'),
            ('a0000000-0000-4000-8000-000000000002', 'Transit Station - Platform Level', 'Canada', 'Quebec', 'Montreal', 'Greater Montreal', '1000 Rue Berri', 'H2L 4L2', 45.5088, -73.5878, '00000000-0000-4000-8000-0000000000ff'),
            ('a0000000-0000-4000-8000-000000000003', 'Fitness Club - Lobby', 'Canada', 'British Columbia', 'Vancouver', 'Metro Vancouver', '800 Robson St', 'V6Z 2E4', 49.2827, -123.1207, '00000000-0000-4000-8000-0000000000ff');

        INSERT INTO media
            (media_id, media_location_id, title, media_owner_name, type_of_display, loop_duration, resolution, width, height, price, daily_impressions, active_days, schedule, status, venue_id, business_id)
        VALUES
            ('b0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000001', 'Mall Entrance Digital Board', 'Envision Demo Media Co.', 'DIGITAL', 15, '1920x1080', 1920, 1080, 249.00, 15000, 30, '{}'::jsonb, 'ACTIVE', '00000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-0000000000ff'),
            ('b0000000-0000-4000-8000-000000000002', 'a0000000-0000-4000-8000-000000000001', 'Mall Food Court Screen', 'Envision Demo Media Co.', 'DIGITAL', 15, '1920x1080', 1920, 1080, 99.00, 8000, 30, '{}'::jsonb, 'ACTIVE', '00000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-0000000000ff'),
            ('b0000000-0000-4000-8000-000000000003', 'a0000000-0000-4000-8000-000000000002', 'Transit Platform Screen', 'Envision Demo Media Co.', 'DIGITAL', 10, '1080x1920', 1080, 1920, 189.00, 42000, 30, '{}'::jsonb, 'ACTIVE', '00000000-0000-4000-8000-000000000002', '00000000-0000-4000-8000-0000000000ff'),
            ('b0000000-0000-4000-8000-000000000004', 'a0000000-0000-4000-8000-000000000003', 'Fitness Club Lobby Display', 'Envision Demo Media Co.', 'DIGITAL', 12, '1920x1080', 1920, 1080, 129.00, 3000, 30, '{}'::jsonb, 'ACTIVE', '00000000-0000-4000-8000-000000000003', '00000000-0000-4000-8000-0000000000ff');

        INSERT INTO bundles
            (bundle_id, name_en, name_fr, description_en, description_fr, ideal_for_en, ideal_for_fr, badge_color, rule_type, rule_value, active, discount_percent)
        VALUES
            ('c0000000-0000-4000-8000-000000000001', 'Toronto Starter', 'Débutant Toronto', 'All active screens in Toronto.', 'Tous les écrans actifs à Toronto.', 'Local advertisers targeting downtown foot traffic.', 'Annonceurs locaux ciblant la circulation piétonne du centre-ville.', '#2563EB', 'CITY', 'Toronto', TRUE, 10),
            ('c0000000-0000-4000-8000-000000000002', 'Greater Montreal Reach', 'Portée du Grand Montréal', 'All active screens across the Greater Montreal region.', 'Tous les écrans actifs de la région du Grand Montréal.', 'Advertisers expanding beyond a single venue.', 'Annonceurs qui s''étendent au-delà d''un seul lieu.', '#059669', 'REGION', 'Greater Montreal', TRUE, 15),
            ('c0000000-0000-4000-8000-000000000003', 'Full Network', 'Réseau complet', 'Every active screen on the network.', 'Chaque écran actif du réseau.', 'National campaigns.', 'Campagnes nationales.', '#7C3AED', 'FULL_NETWORK', NULL, TRUE, 5);

    END IF;
END $$;

-- Mohamed's dev business (Visual Impact), so he can test locally as an existing
-- teammate rather than a brand-new signup. The Auth0 sub below is his real one
-- (confirmed directly, not looked up from prod — pulling prod DB credentials was
-- deliberately blocked for this session). Address/organization_size are placeholder
-- values, not a real mirror of prod — they don't affect login matching and don't need
-- to be accurate for local testing. Both media_owner and advertiser are set so both
-- dashboards work.
--
-- Guarded on employee.user_id, not our own business_id: that sub may already have an
-- employee row on a given machine from prior real usage (e.g. Mohamed's own local DB),
-- and employee.user_id is UNIQUE — inserting again would fail. If it's already linked
-- to any business, this block is a no-op; whatever's there already satisfies the goal.
DO $$
DECLARE
    v_address_id INTEGER;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM employee WHERE user_id = 'auth0|6972cb215b943c997508c737') THEN

        INSERT INTO address (street, city, state, zip_code, country)
        VALUES ('123 Local Dev St', 'Toronto', 'Ontario', 'M5H 2N2', 'Canada')
        RETURNING id INTO v_address_id;

        INSERT INTO business
            (business_id, name, organization_size, address_id, owner_id, media_owner, advertiser, verified, active)
        VALUES
            ('d0000000-0000-4000-8000-000000000001', 'Visual Impact', 'MEDIUM', v_address_id, 'auth0|6972cb215b943c997508c737', TRUE, TRUE, TRUE, TRUE);

        INSERT INTO employee (employee_id, user_id, business_id)
        VALUES ('e0000000-0000-4000-8000-000000000001', 'auth0|6972cb215b943c997508c737', 'd0000000-0000-4000-8000-000000000001');

    END IF;
END $$;
