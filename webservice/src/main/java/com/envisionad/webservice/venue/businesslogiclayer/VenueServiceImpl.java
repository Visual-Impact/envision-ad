package com.envisionad.webservice.venue.businesslogiclayer;

import com.envisionad.webservice.media.DataAccessLayer.MediaRepository;
import com.envisionad.webservice.venue.dataaccesslayer.Venue;
import com.envisionad.webservice.venue.dataaccesslayer.VenueRepository;
import com.envisionad.webservice.venue.exceptions.VenueNotFoundException;
import com.envisionad.webservice.venue.presentationlayer.models.VenueRequestModel;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class VenueServiceImpl implements VenueService {

    private final VenueRepository venueRepository;
    private final MediaRepository mediaRepository;

    public VenueServiceImpl(VenueRepository venueRepository, MediaRepository mediaRepository) {
        this.venueRepository = venueRepository;
        this.mediaRepository = mediaRepository;
    }

    @Override
    public List<Venue> getAllVenues(String locale) {
        if ("fr".equalsIgnoreCase(locale)) {
            return venueRepository.findAllByOrderByNameFrAsc();
        }
        return venueRepository.findAllByOrderByNameEnAsc();
    }

    @Override
    public Venue getVenueByVenueId(String venueId) {
        return findVenueByVenueId(venueId)
                .orElseThrow(() -> new VenueNotFoundException(venueId));
    }

    @Override
    public Optional<Venue> findVenueByVenueId(String venueId) {
        if (venueId == null) {
            return Optional.empty();
        }
        return venueRepository.findByVenueId(venueId);
    }

    @Override
    public Venue createVenue(Venue venue) {
        return venueRepository.save(venue);
    }

    @Override
    public Venue updateVenue(String venueId, VenueRequestModel request) {
        Venue existing = venueRepository.findByVenueId(venueId)
                .orElseThrow(() -> new VenueNotFoundException(venueId));

        existing.setNameEn(request.getNameEn());
        existing.setNameFr(request.getNameFr());
        existing.setColorCode(request.getColorCode());

        return venueRepository.save(existing);
    }

    @Override
    public void deleteVenue(String venueId) {
        Venue venue = venueRepository.findByVenueId(venueId)
                .orElseThrow(() -> new VenueNotFoundException(venueId));

        // ON DELETE SET NULL in the DB handles nulling out media.venue_id
        venueRepository.delete(venue);
    }

    @Override
    public long getMediaCountForVenue(String venueId) {
        return mediaRepository.countByVenueId(venueId);
    }
}
