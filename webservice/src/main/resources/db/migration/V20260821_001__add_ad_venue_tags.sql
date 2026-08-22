-- P7: venue tags on ads/creatives.
--
-- Mixed FK convention is deliberate — each column mirrors its nearest existing
-- analogue rather than inventing a third rule:
--   ad_id    -> ads(id), the internal serial, matching ads.ad_campaign_ref_id -> ad_campaigns(id)
--   venue_id -> venue(venue_id), the public UUID, matching media.venue_id -> venue(venue_id)
--
-- Both sides cascade. The venue_id cascade is a production backstop only:
-- VenueServiceImpl.deleteVenue() also untags affected ads in application code,
-- because the test profile builds its schema from the JPA entities (ddl-auto:
-- create, Flyway disabled) and Hibernate emits this join table's FKs WITHOUT
-- a cascade. Without the app-level cleanup the two environments disagree.
CREATE TABLE ad_venue_tags
(
    ad_id    INTEGER     NOT NULL REFERENCES ads (id) ON DELETE CASCADE,
    venue_id VARCHAR(36) NOT NULL REFERENCES venue (venue_id) ON DELETE CASCADE,
    PRIMARY KEY (ad_id, venue_id)
);
