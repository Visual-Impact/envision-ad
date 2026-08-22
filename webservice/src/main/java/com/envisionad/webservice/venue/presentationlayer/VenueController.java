package com.envisionad.webservice.venue.presentationlayer;

import com.envisionad.webservice.venue.businesslogiclayer.VenueService;
import com.envisionad.webservice.venue.dataaccesslayer.Venue;
import com.envisionad.webservice.venue.mappinglayer.VenueRequestMapper;
import com.envisionad.webservice.venue.mappinglayer.VenueResponseMapper;
import com.envisionad.webservice.venue.presentationlayer.models.VenueAdCountResponseModel;
import com.envisionad.webservice.venue.presentationlayer.models.VenueRequestModel;
import com.envisionad.webservice.venue.presentationlayer.models.VenueResponseModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/venues")
@CrossOrigin(origins = {"http://localhost:3000", "https://envision-ad.ca"})
public class VenueController {

    private final VenueService venueService;
    private final VenueRequestMapper requestMapper;
    private final VenueResponseMapper responseMapper;

    public VenueController(VenueService venueService, VenueRequestMapper requestMapper, VenueResponseMapper responseMapper) {
        this.venueService = venueService;
        this.requestMapper = requestMapper;
        this.responseMapper = responseMapper;
    }

    @GetMapping
    public ResponseEntity<List<VenueResponseModel>> getAllVenues(
            @RequestParam(required = false, defaultValue = "en") String locale) {
        List<Venue> venues = venueService.getAllVenues(locale);
        List<VenueResponseModel> response = responseMapper.entityListToResponseModelList(
                venues, venueService::getMediaCountForVenue);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{venueId}")
    public ResponseEntity<VenueResponseModel> getVenueByVenueId(@PathVariable String venueId) {
        Venue venue = venueService.getVenueByVenueId(venueId);
        long mediaCount = venueService.getMediaCountForVenue(venueId);
        return ResponseEntity.ok(responseMapper.entityToResponseModel(venue, mediaCount));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('manage:venues')")
    public ResponseEntity<VenueResponseModel> createVenue(@RequestBody VenueRequestModel request) {
        Venue entity = requestMapper.requestModelToEntity(request);
        Venue saved = venueService.createVenue(entity);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(responseMapper.entityToResponseModel(saved, 0));
    }

    @PutMapping("/{venueId}")
    @PreAuthorize("hasAuthority('manage:venues')")
    public ResponseEntity<VenueResponseModel> updateVenue(
            @PathVariable String venueId,
            @RequestBody VenueRequestModel request) {
        Venue updated = venueService.updateVenue(venueId, request);
        long mediaCount = venueService.getMediaCountForVenue(venueId);
        return ResponseEntity.ok(responseMapper.entityToResponseModel(updated, mediaCount));
    }

    @DeleteMapping("/{venueId}")
    @PreAuthorize("hasAuthority('manage:venues')")
    public ResponseEntity<Void> deleteVenue(@PathVariable String venueId) {
        venueService.deleteVenue(venueId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{venueId}/media-count")
    @PreAuthorize("hasAuthority('manage:venues')")
    public ResponseEntity<Long> getMediaCount(@PathVariable String venueId) {
        return ResponseEntity.ok(venueService.getMediaCountForVenue(venueId));
    }

    @GetMapping("/{venueId}/ad-count")
    @PreAuthorize("hasAuthority('manage:venues')")
    public ResponseEntity<VenueAdCountResponseModel> getAdCount(@PathVariable String venueId) {
        return ResponseEntity.ok(new VenueAdCountResponseModel(venueService.getAdCountForVenue(venueId)));
    }
}
