package com.envisionad.webservice.venue.businesslogiclayer;

import com.envisionad.webservice.advertisement.dataaccesslayer.Ad;
import com.envisionad.webservice.advertisement.dataaccesslayer.AdRepository;
import com.envisionad.webservice.media.DataAccessLayer.MediaRepository;
import com.envisionad.webservice.venue.dataaccesslayer.Venue;
import com.envisionad.webservice.venue.dataaccesslayer.VenueRepository;
import com.envisionad.webservice.venue.exceptions.VenueNotFoundException;
import com.envisionad.webservice.venue.presentationlayer.models.VenueRequestModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class VenueServiceImpl implements VenueService {

    private final VenueRepository venueRepository;
    private final MediaRepository mediaRepository;
    private final AdRepository adRepository;

    public VenueServiceImpl(VenueRepository venueRepository, MediaRepository mediaRepository,
                            AdRepository adRepository) {
        this.venueRepository = venueRepository;
        this.mediaRepository = mediaRepository;
        this.adRepository = adRepository;
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
    @Transactional
    public void deleteVenue(String venueId) {
        Venue venue = venueRepository.findByVenueId(venueId)
                .orElseThrow(() -> new VenueNotFoundException(venueId));

        // ON DELETE SET NULL in the DB handles nulling out media.venue_id

        // Ad tags, unlike media.venue_id, need explicit cleanup. media.venue_id is a plain
        // String column with no JPA association, so Hibernate never emits a FK for it and
        // the DB constraint is the only thing involved. ad_venue_tags IS a real @JoinTable,
        // so the test profile (ddl-auto: create, Flyway disabled) generates its FKs from the
        // entity — without the migration's ON DELETE CASCADE. Relying on the DB alone would
        // mean venue deletion succeeds in production and fails in tests. Untagging here keeps
        // both environments identical; the migration's cascade stays as a backstop.
        List<Ad> taggedAds = adRepository.findByVenues_VenueId(venueId);
        if (!taggedAds.isEmpty()) {
            taggedAds.forEach(ad -> ad.getVenues().removeIf(v -> venueId.equals(v.getVenueId())));
            adRepository.saveAll(taggedAds);
        }

        venueRepository.delete(venue);
    }

    @Override
    public long getMediaCountForVenue(String venueId) {
        return mediaRepository.countByVenueId(venueId);
    }

    @Override
    public long getAdCountForVenue(String venueId) {
        return adRepository.countByVenues_VenueId(venueId);
    }
}
