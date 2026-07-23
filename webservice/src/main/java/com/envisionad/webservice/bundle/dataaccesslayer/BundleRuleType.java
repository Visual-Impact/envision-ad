package com.envisionad.webservice.bundle.dataaccesslayer;

/**
 * How a bundle selects its candidate media set. FULL_NETWORK matches every ACTIVE
 * media; the others match {@link Bundle#getRuleValue()} against, respectively,
 * media_location.city, media_location.region, and media.venue_id.
 */
public enum BundleRuleType {
    CITY,
    REGION,
    VENUE,
    FULL_NETWORK
}
