package com.envisionad.webservice.venue.businesslogiclayer;

import com.envisionad.webservice.media.DataAccessLayer.MediaRepository;
import com.envisionad.webservice.venue.dataaccesslayer.Venue;
import com.envisionad.webservice.venue.dataaccesslayer.VenueRepository;
import com.envisionad.webservice.venue.exceptions.VenueNotFoundException;
import com.envisionad.webservice.venue.presentationlayer.models.VenueRequestModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VenueServiceUnitTest {

    @InjectMocks
    private VenueServiceImpl venueService;

    @Mock
    private VenueRepository venueRepository;

    @Mock
    private MediaRepository mediaRepository;

    private Venue testVenue;

    @BeforeEach
    void setUp() {
        testVenue = new Venue();
        testVenue.setId(1);
        testVenue.setVenueId("test-venue-id");
        testVenue.setNameEn("Barbershop");
        testVenue.setNameFr("Salon de coiffure");
        testVenue.setColorCode("#FF5733");
    }

    @Test
    void getAllVenues_englishLocale_returnsOrderedByNameEn() {
        when(venueRepository.findAllByOrderByNameEnAsc()).thenReturn(List.of(testVenue));

        List<Venue> result = venueService.getAllVenues("en");

        assertEquals(1, result.size());
        assertEquals("Barbershop", result.get(0).getNameEn());
        verify(venueRepository).findAllByOrderByNameEnAsc();
    }

    @Test
    void getAllVenues_frenchLocale_returnsOrderedByNameFr() {
        when(venueRepository.findAllByOrderByNameFrAsc()).thenReturn(List.of(testVenue));

        List<Venue> result = venueService.getAllVenues("fr");

        assertEquals(1, result.size());
        verify(venueRepository).findAllByOrderByNameFrAsc();
    }

    @Test
    void getAllVenues_nullLocale_defaultsToEnglish() {
        when(venueRepository.findAllByOrderByNameEnAsc()).thenReturn(List.of(testVenue));

        venueService.getAllVenues(null);

        verify(venueRepository).findAllByOrderByNameEnAsc();
    }

    @Test
    void getVenueByVenueId_existingVenue_returnsVenue() {
        when(venueRepository.findByVenueId("test-venue-id")).thenReturn(Optional.of(testVenue));

        Venue result = venueService.getVenueByVenueId("test-venue-id");

        assertEquals("Barbershop", result.getNameEn());
    }

    @Test
    void getVenueByVenueId_nonExistingVenue_throwsException() {
        when(venueRepository.findByVenueId("bad-id")).thenReturn(Optional.empty());

        assertThrows(VenueNotFoundException.class, () -> venueService.getVenueByVenueId("bad-id"));
    }

    @Test
    void findVenueByVenueId_existingVenue_returnsIt() {
        when(venueRepository.findByVenueId("test-venue-id")).thenReturn(Optional.of(testVenue));

        assertEquals("Barbershop", venueService.findVenueByVenueId("test-venue-id").orElseThrow().getNameEn());
    }

    @Test
    void findVenueByVenueId_nonExistingVenue_isEmptyRatherThanThrowing() {
        when(venueRepository.findByVenueId("bad-id")).thenReturn(Optional.empty());

        assertTrue(venueService.findVenueByVenueId("bad-id").isEmpty());
    }

    @Test
    void findVenueByVenueId_nullId_isEmptyWithoutHittingTheRepository() {
        // A FULL_NETWORK bundle carries no rule value; callers shouldn't have to guard.
        assertTrue(venueService.findVenueByVenueId(null).isEmpty());

        verify(venueRepository, never()).findByVenueId(any());
    }

    @Test
    void createVenue_savesAndReturnsVenue() {
        when(venueRepository.save(any(Venue.class))).thenReturn(testVenue);

        Venue result = venueService.createVenue(testVenue);

        assertEquals("Barbershop", result.getNameEn());
        verify(venueRepository).save(testVenue);
    }

    @Test
    void updateVenue_existingVenue_updatesFields() {
        when(venueRepository.findByVenueId("test-venue-id")).thenReturn(Optional.of(testVenue));
        when(venueRepository.save(any(Venue.class))).thenReturn(testVenue);

        VenueRequestModel request = new VenueRequestModel();
        request.setNameEn("Updated Name");
        request.setNameFr("Nom mis à jour");
        request.setColorCode("#000000");

        Venue result = venueService.updateVenue("test-venue-id", request);

        assertEquals("Updated Name", testVenue.getNameEn());
        assertEquals("Nom mis à jour", testVenue.getNameFr());
        assertEquals("#000000", testVenue.getColorCode());
        verify(venueRepository).save(testVenue);
    }

    @Test
    void updateVenue_nonExistingVenue_throwsException() {
        when(venueRepository.findByVenueId("bad-id")).thenReturn(Optional.empty());

        VenueRequestModel request = new VenueRequestModel();
        request.setNameEn("Name");
        request.setNameFr("Nom");
        request.setColorCode("#000000");

        assertThrows(VenueNotFoundException.class, () -> venueService.updateVenue("bad-id", request));
    }

    @Test
    void deleteVenue_existingVenue_deletesSuccessfully() {
        when(venueRepository.findByVenueId("test-venue-id")).thenReturn(Optional.of(testVenue));

        venueService.deleteVenue("test-venue-id");

        verify(venueRepository).delete(testVenue);
    }

    @Test
    void deleteVenue_nonExistingVenue_throwsException() {
        when(venueRepository.findByVenueId("bad-id")).thenReturn(Optional.empty());

        assertThrows(VenueNotFoundException.class, () -> venueService.deleteVenue("bad-id"));
    }

    @Test
    void getMediaCountForVenue_returnsCount() {
        when(mediaRepository.countByVenueId("test-venue-id")).thenReturn(5L);

        long count = venueService.getMediaCountForVenue("test-venue-id");

        assertEquals(5L, count);
    }
}
