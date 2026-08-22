package com.envisionad.webservice.venue.presentationlayer.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Wrapper for the ad-count endpoint. Note this deliberately differs in shape from the
 * sibling media-count endpoint, which returns a bare JSON number — the P7 brief pins
 * the object form here. Flagged rather than silently normalized either way.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VenueAdCountResponseModel {
    private long adCount;
}
