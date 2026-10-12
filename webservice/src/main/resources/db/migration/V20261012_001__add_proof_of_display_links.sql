-- P12 (proof of display via link submission). Replaces the stateless photo-email flow, which
-- persisted nothing, so there is no data to migrate.
--
-- One row per subscription, ever: a media owner points the subscription at a page they maintain
-- (typically Canva) and edits the URL in place. Row present = link set; row absent = not set.
-- No status column and no history (brief 12, Data Model).
--
-- ON DELETE CASCADE mirrors bundle_subscription_items, the only other table that hangs off
-- bundle_subscriptions: a hard-deleted subscription (as in the 2026-10-04 prod cleanup) takes its
-- link with it rather than failing on this FK (P12-PROGRESS D6).
CREATE TABLE proof_of_display_links
(
    id                 UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    subscription_id    VARCHAR(36)  NOT NULL UNIQUE
        REFERENCES bundle_subscriptions (subscription_id) ON DELETE CASCADE,
    url                TEXT         NOT NULL,
    updated_by_user_id VARCHAR(255) NULL,
    created_at         TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at         TIMESTAMP    NOT NULL DEFAULT now()
);
