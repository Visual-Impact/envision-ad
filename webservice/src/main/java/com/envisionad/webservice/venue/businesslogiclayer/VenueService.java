package com.envisionad.webservice.venue.businesslogiclayer;

import com.envisionad.webservice.venue.dataaccesslayer.Venue;
import com.envisionad.webservice.venue.presentationlayer.models.VenueRequestModel;

import java.util.List;
import java.util.Optional;

public interface VenueService {

    List<Venue> getAllVenues(String locale);

    Venue getVenueByVenueId(String venueId);

    /**
     * Lookup for callers that treat a missing venue as a normal outcome rather than
     * a 404 — e.g. resolving a bundle's VENUE rule to a display name, where a stale
     * reference should degrade to the raw id, not fail the whole response.
     */
    Optional<Venue> findVenueByVenueId(String venueId);

    Venue createVenue(Venue venue);

    Venue updateVenue(String venueId, VenueRequestModel request);

    void deleteVenue(String venueId);

    long getMediaCountForVenue(String venueId);
}
