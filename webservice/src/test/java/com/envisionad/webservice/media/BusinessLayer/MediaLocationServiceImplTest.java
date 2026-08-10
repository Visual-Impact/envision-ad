package com.envisionad.webservice.media.BusinessLayer;

import com.envisionad.webservice.business.businesslogiclayer.BusinessService;
import com.envisionad.webservice.business.presentationlayer.models.BusinessResponseModel;
import com.envisionad.webservice.media.DataAccessLayer.Media;
import com.envisionad.webservice.media.DataAccessLayer.MediaLocation;
import com.envisionad.webservice.media.DataAccessLayer.MediaLocationRepository;
import com.envisionad.webservice.media.DataAccessLayer.Status;
import com.envisionad.webservice.media.exceptions.GeocodingServiceUnavailableException;
import com.envisionad.webservice.media.exceptions.MediaLocationDeletionNotAllowedException;
import com.envisionad.webservice.media.exceptions.MediaLocationValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaLocationServiceImplTest {

    @Mock
    private MediaLocationRepository mediaLocationRepository;
    @Mock
    private BusinessService businessService;
    @Mock
    private GeocodingService geocodingService;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private MediaLocationServiceImpl mediaLocationService;

    private MediaLocation mediaLocation;
    private final UUID businessId = UUID.randomUUID();
    private final String geocodingJson = "[{\"lat\":\"10.0\", \"lon\":\"20.0\"}]";

    @BeforeEach
    void setUp() {
        mediaLocation = new MediaLocation();
        mediaLocation.setStreet("123 Main St");
        mediaLocation.setCity("City");
        mediaLocation.setProvince("Province");
        mediaLocation.setCountry("Country");
        mediaLocation.setPostalCode("12345");
    }

    private void mockAuthenticatedBusiness(Jwt jwt) {
        when(jwt.getSubject()).thenReturn("auth0|123");
        BusinessResponseModel businessModel = new BusinessResponseModel();
        businessModel.setBusinessId(businessId.toString());
        when(businessService.getBusinessByUserId(any(), anyString())).thenReturn(businessModel);
    }

    private void mockGeocodedCoordinates() throws Exception {
        JsonNode rootNode = mock(JsonNode.class);
        JsonNode firstResult = mock(JsonNode.class);
        JsonNode latNode = mock(JsonNode.class);
        JsonNode lonNode = mock(JsonNode.class);

        when(objectMapper.readTree(anyString())).thenReturn(rootNode);
        when(rootNode.isArray()).thenReturn(true);
        when(rootNode.size()).thenReturn(1);
        when(rootNode.get(0)).thenReturn(firstResult);
        when(firstResult.has("lat")).thenReturn(true);
        when(firstResult.get("lat")).thenReturn(latNode);
        when(latNode.asText()).thenReturn("10.0");
        when(firstResult.has("lon")).thenReturn(true);
        when(firstResult.get("lon")).thenReturn(lonNode);
        when(lonNode.asText()).thenReturn("20.0");
    }

    @Test
    void createMediaLocation_StructuredMatch_SavesLocationWithCoordinates() throws Exception {
        Jwt jwt = mock(Jwt.class);
        mockAuthenticatedBusiness(jwt);

        when(geocodingService.geocodeStructuredAddress(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(geocodingJson));
        when(mediaLocationRepository.save(any(MediaLocation.class))).thenReturn(mediaLocation);
        mockGeocodedCoordinates();

        MediaLocation result = mediaLocationService.createMediaLocation(mediaLocation, jwt);

        assertNotNull(result);
        assertEquals(10.0, result.getLatitude());
        assertEquals(20.0, result.getLongitude());
        assertEquals(geocodingJson, result.getGeocodingResponse());
        verify(geocodingService, never()).geocodeAddress(anyString());
        verify(mediaLocationRepository).save(mediaLocation);
    }

    @Test
    void createMediaLocation_StructuredMatchMisses_FallsBackToFreeTextQuery() throws Exception {
        Jwt jwt = mock(Jwt.class);
        mockAuthenticatedBusiness(jwt);

        when(geocodingService.geocodeStructuredAddress(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(geocodingService.geocodeAddress(eq("123 Main St, City, Province, Country, 12345")))
                .thenReturn(Optional.of(geocodingJson));
        when(mediaLocationRepository.save(any(MediaLocation.class))).thenReturn(mediaLocation);
        mockGeocodedCoordinates();

        MediaLocation result = mediaLocationService.createMediaLocation(mediaLocation, jwt);

        assertNotNull(result);
        assertEquals(10.0, result.getLatitude());
        assertEquals(20.0, result.getLongitude());
        verify(mediaLocationRepository).save(mediaLocation);
    }

    @Test
    void createMediaLocation_NoGeocodingMatch_ThrowsValidationExceptionForAllAddressFields() {
        Jwt jwt = mock(Jwt.class);
        mockAuthenticatedBusiness(jwt);

        when(geocodingService.geocodeStructuredAddress(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(geocodingService.geocodeAddress(anyString())).thenReturn(Optional.empty());

        MediaLocationValidationException exception = assertThrows(MediaLocationValidationException.class,
                () -> mediaLocationService.createMediaLocation(mediaLocation, jwt));

        assertTrue(exception.getMessage().contains("couldn't verify this address"));
        assertEquals(5, exception.getFieldErrors().size());
        assertTrue(exception.getFieldErrors().containsKey("street"));
        assertTrue(exception.getFieldErrors().containsKey("city"));
        assertTrue(exception.getFieldErrors().containsKey("province"));
        assertTrue(exception.getFieldErrors().containsKey("country"));
        assertTrue(exception.getFieldErrors().containsKey("postalCode"));
        verify(mediaLocationRepository, never()).save(any(MediaLocation.class));
    }

    @Test
    void createMediaLocation_GeocodingUnavailable_ThrowsServiceUnavailableException() {
        Jwt jwt = mock(Jwt.class);
        mockAuthenticatedBusiness(jwt);

        when(geocodingService.geocodeStructuredAddress(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new GeocodingServiceUnavailableException("Address validation service is temporarily unavailable.",
                        new RuntimeException("timeout")));

        GeocodingServiceUnavailableException exception = assertThrows(GeocodingServiceUnavailableException.class,
                () -> mediaLocationService.createMediaLocation(mediaLocation, jwt));

        assertEquals("Address validation service is temporarily unavailable. Please try again shortly.",
                exception.getMessage());
        verify(mediaLocationRepository, never()).save(any(MediaLocation.class));
    }

    @Test
    void createMediaLocation_InvalidCoordinateFormat_ThrowsExceptionAndDoesNotSave() throws Exception {
        Jwt jwt = mock(Jwt.class);
        mockAuthenticatedBusiness(jwt);

        when(geocodingService.geocodeStructuredAddress(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(geocodingJson));

        JsonNode rootNode = mock(JsonNode.class);
        JsonNode firstResult = mock(JsonNode.class);
        JsonNode latNode = mock(JsonNode.class);

        when(objectMapper.readTree(anyString())).thenReturn(rootNode);
        when(rootNode.isArray()).thenReturn(true);
        when(rootNode.size()).thenReturn(1);
        when(rootNode.get(0)).thenReturn(firstResult);
        when(firstResult.has("lat")).thenReturn(true);
        when(firstResult.has("lon")).thenReturn(true);
        when(firstResult.get("lat")).thenReturn(latNode);
        when(latNode.asText()).thenReturn("invalid-lat");

        MediaLocationValidationException exception = assertThrows(MediaLocationValidationException.class,
                () -> mediaLocationService.createMediaLocation(mediaLocation, jwt));

        assertTrue(exception.getMessage().startsWith("Address was matched but coordinates could not be determined."));
        assertTrue(exception.getFieldErrors().isEmpty());
        verify(mediaLocationRepository, never()).save(any(MediaLocation.class));
    }

    @Test
    void createMediaLocation_MissingPostalCode_ThrowsValidationException() {
        Jwt jwt = mock(Jwt.class);
        mockAuthenticatedBusiness(jwt);
        mediaLocation.setPostalCode("   ");

        MediaLocationValidationException exception = assertThrows(MediaLocationValidationException.class,
                () -> mediaLocationService.createMediaLocation(mediaLocation, jwt));

        assertEquals("Please provide a valid address including street, city, province/state, country, and postal code.",
                exception.getMessage());
        assertEquals("Postal code is required.", exception.getFieldErrors().get("postalCode"));
        verify(mediaLocationRepository, never()).save(any(MediaLocation.class));
        verifyNoInteractions(geocodingService);
    }

    @Test
    void createMediaLocation_ManualCoordinates_SkipsGeocodingAndTrustsSubmittedCoordinates() {
        Jwt jwt = mock(Jwt.class);
        mockAuthenticatedBusiness(jwt);

        mediaLocation.setManualCoordinates(true);
        mediaLocation.setLatitude(45.5);
        mediaLocation.setLongitude(-73.6);
        when(mediaLocationRepository.save(any(MediaLocation.class))).thenReturn(mediaLocation);

        MediaLocation result = mediaLocationService.createMediaLocation(mediaLocation, jwt);

        assertNotNull(result);
        assertEquals(45.5, result.getLatitude());
        assertEquals(-73.6, result.getLongitude());
        assertNull(result.getGeocodingResponse());
        verifyNoInteractions(geocodingService);
        verify(mediaLocationRepository).save(mediaLocation);
    }

    @Test
    void createMediaLocation_ManualCoordinatesOutOfRange_ThrowsValidationException() {
        Jwt jwt = mock(Jwt.class);
        mockAuthenticatedBusiness(jwt);

        mediaLocation.setManualCoordinates(true);
        mediaLocation.setLatitude(200.0);
        mediaLocation.setLongitude(-73.6);

        MediaLocationValidationException exception = assertThrows(MediaLocationValidationException.class,
                () -> mediaLocationService.createMediaLocation(mediaLocation, jwt));

        assertTrue(exception.getFieldErrors().containsKey("latitude"));
        verifyNoInteractions(geocodingService);
        verify(mediaLocationRepository, never()).save(any(MediaLocation.class));
    }

    @Test
    void createMediaLocation_ManualCoordinatesMissing_ThrowsValidationException() {
        Jwt jwt = mock(Jwt.class);
        mockAuthenticatedBusiness(jwt);

        mediaLocation.setManualCoordinates(true);

        MediaLocationValidationException exception = assertThrows(MediaLocationValidationException.class,
                () -> mediaLocationService.createMediaLocation(mediaLocation, jwt));

        assertTrue(exception.getFieldErrors().containsKey("latitude"));
        assertTrue(exception.getFieldErrors().containsKey("longitude"));
        verify(mediaLocationRepository, never()).save(any(MediaLocation.class));
    }

    @Test
    void createMediaLocation_WhenResolvedBusinessIdIsNull_ThrowsIllegalArgumentException() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("auth0|123");
        when(businessService.getBusinessByUserId(any(), anyString())).thenReturn(null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> mediaLocationService.createMediaLocation(mediaLocation, jwt));

        assertEquals("Business ID is required to create a media location.", exception.getMessage());
        verify(mediaLocationRepository, never()).save(any(MediaLocation.class));
        verifyNoInteractions(geocodingService);
    }

    @Test
    void createMediaLocation_WhenProvidedBusinessIdDiffersFromJwt_ThrowsIllegalArgumentException() {
        Jwt jwt = mock(Jwt.class);
        mockAuthenticatedBusiness(jwt);

        mediaLocation.setBusinessId(UUID.randomUUID());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> mediaLocationService.createMediaLocation(mediaLocation, jwt));

        assertEquals("Provided business ID does not match the authenticated user's business.", exception.getMessage());
        verify(mediaLocationRepository, never()).save(any(MediaLocation.class));
    }

    @Test
    void getAllMediaLocations_WithJwtAndMatchingBusinessId_ReturnsLocations() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("auth0|123");
        BusinessResponseModel businessModel = new BusinessResponseModel();
        businessModel.setBusinessId(businessId.toString());
        when(businessService.getBusinessByUserId(jwt, "auth0|123")).thenReturn(businessModel);

        List<MediaLocation> expected = List.of(new MediaLocation());
        when(mediaLocationRepository.findAllByBusinessId(businessId)).thenReturn(expected);

        List<MediaLocation> result = mediaLocationService.getAllMediaLocations(jwt, businessId.toString());

        assertSame(expected, result);
        verify(businessService).getBusinessByUserId(jwt, "auth0|123");
    }

    @Test
    void getAllMediaLocations_WithJwtAndNoBusinessId_ResolvesBusinessAndReturnsLocations() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("auth0|123");
        BusinessResponseModel businessModel = new BusinessResponseModel();
        businessModel.setBusinessId(businessId.toString());
        when(businessService.getBusinessByUserId(jwt, "auth0|123")).thenReturn(businessModel);

        List<MediaLocation> expected = List.of(new MediaLocation());
        when(mediaLocationRepository.findAllByBusinessId(businessId)).thenReturn(expected);

        List<MediaLocation> result = mediaLocationService.getAllMediaLocations(jwt, null);

        assertSame(expected, result);
        verify(businessService).getBusinessByUserId(jwt, "auth0|123");
    }

    @Test
    void getAllMediaLocations_WithNoJwtAndNoBusinessId_ThrowsIllegalArgumentException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> mediaLocationService.getAllMediaLocations(null, null));

        assertEquals("Business ID is required", exception.getMessage());
        verify(mediaLocationRepository, never()).findAllByBusinessId(any(UUID.class));
    }

    @Test
    void getAllMediaLocations_WithNoJwtAndExplicitBusinessId_ThrowsIllegalArgumentException() {
        UUID targetBusinessId = UUID.randomUUID();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> mediaLocationService.getAllMediaLocations(null, targetBusinessId.toString()));

        assertEquals("Business ID is required", exception.getMessage());
        verify(mediaLocationRepository, never()).findAllByBusinessId(any(UUID.class));
    }

    @Test
    void getAllMediaLocations_WithJwtAndMismatchedBusinessId_ThrowsIllegalArgumentException() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("auth0|123");
        BusinessResponseModel businessModel = new BusinessResponseModel();
        businessModel.setBusinessId(businessId.toString());
        when(businessService.getBusinessByUserId(jwt, "auth0|123")).thenReturn(businessModel);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> mediaLocationService.getAllMediaLocations(jwt, UUID.randomUUID().toString()));

        assertEquals("Requested business ID does not match the authenticated user's business.", exception.getMessage());
        verify(mediaLocationRepository, never()).findAllByBusinessId(any(UUID.class));
    }

    @Test
    void getAllMediaLocations_WhenBusinessLookupThrows_ThrowsIllegalArgumentException() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("auth0|123");
        when(businessService.getBusinessByUserId(jwt, "auth0|123"))
                .thenThrow(new RuntimeException("lookup failed"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> mediaLocationService.getAllMediaLocations(jwt, null));

        assertEquals("Business ID is required", exception.getMessage());
        verify(mediaLocationRepository, never()).findAllByBusinessId(any(UUID.class));
    }

    @Test
    void getAllMediaLocations_WhenBusinessLookupReturnsNull_ThrowsIllegalArgumentException() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("auth0|123");
        when(businessService.getBusinessByUserId(jwt, "auth0|123")).thenReturn(null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> mediaLocationService.getAllMediaLocations(jwt, null));

        assertEquals("Business ID is required", exception.getMessage());
        verify(mediaLocationRepository, never()).findAllByBusinessId(any(UUID.class));
    }

    @Test
    void getMediaLocationById_WhenFound_ReturnsLocation() {
        UUID id = UUID.randomUUID();
        MediaLocation existing = new MediaLocation();
        when(mediaLocationRepository.findById(id)).thenReturn(Optional.of(existing));

        MediaLocation result = mediaLocationService.getMediaLocationById(id);

        assertSame(existing, result);
    }

    @Test
    void getMediaLocationById_WhenMissing_ReturnsNull() {
        UUID id = UUID.randomUUID();
        when(mediaLocationRepository.findById(id)).thenReturn(Optional.empty());

        MediaLocation result = mediaLocationService.getMediaLocationById(id);

        assertNull(result);
    }

    @Test
    void updateMediaLocation_WhenMissing_ReturnsNull() {
        UUID id = UUID.randomUUID();
        when(mediaLocationRepository.findById(id)).thenReturn(Optional.empty());

        MediaLocation result = mediaLocationService.updateMediaLocation(id, mediaLocation);

        assertNull(result);
        verify(mediaLocationRepository, never()).save(any(MediaLocation.class));
        verifyNoInteractions(geocodingService);
    }

    @Test
    void updateMediaLocation_WhenFound_UsesExistingBusinessIdAndSaves() throws Exception {
        UUID id = UUID.randomUUID();
        UUID existingBusinessId = UUID.randomUUID();
        MediaLocation existing = new MediaLocation();
        existing.setBusinessId(existingBusinessId);
        when(mediaLocationRepository.findById(id)).thenReturn(Optional.of(existing));
        when(mediaLocationRepository.save(any(MediaLocation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(geocodingService.geocodeStructuredAddress(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(geocodingJson));
        mockGeocodedCoordinates();

        MediaLocation result = mediaLocationService.updateMediaLocation(id, mediaLocation);

        assertNotNull(result);
        assertEquals(id, result.getId());
        assertEquals(existingBusinessId, result.getBusinessId());
        assertEquals(10.0, result.getLatitude());
        assertEquals(20.0, result.getLongitude());
        verify(mediaLocationRepository).save(mediaLocation);
    }

    @Test
    void deleteMediaLocation_WhenMissing_DoesNothing() {
        UUID id = UUID.randomUUID();
        when(mediaLocationRepository.findById(id)).thenReturn(Optional.empty());

        mediaLocationService.deleteMediaLocation(id);

        verify(mediaLocationRepository, never()).delete(any(MediaLocation.class));
    }

    @Test
    void deleteMediaLocation_WhenFound_DeletesLocation() {
        UUID id = UUID.randomUUID();
        MediaLocation existing = new MediaLocation();
        when(mediaLocationRepository.findById(id)).thenReturn(Optional.of(existing));

        mediaLocationService.deleteMediaLocation(id);

        verify(mediaLocationRepository).delete(existing);
    }

    @Test
    void deleteMediaLocation_WhenActiveMediaAssigned_ThrowsMediaLocationDeletionNotAllowedException() {
        UUID id = UUID.randomUUID();
        MediaLocation existing = new MediaLocation();
        Media activeMedia = new Media();
        activeMedia.setStatus(Status.ACTIVE);
        existing.setMediaList(List.of(activeMedia));
        when(mediaLocationRepository.findById(id)).thenReturn(Optional.of(existing));

        MediaLocationDeletionNotAllowedException exception = assertThrows(
                MediaLocationDeletionNotAllowedException.class,
                () -> mediaLocationService.deleteMediaLocation(id));

        assertEquals("Cannot delete media location while active, pending, or rejected media are assigned. Please delete those media first.", exception.getMessage());
        verify(mediaLocationRepository, never()).delete(any(MediaLocation.class));
    }

    @Test
    void deleteMediaLocation_WhenPendingMediaAssigned_ThrowsMediaLocationDeletionNotAllowedException() {
        UUID id = UUID.randomUUID();
        MediaLocation existing = new MediaLocation();
        Media pendingMedia = new Media();
        pendingMedia.setStatus(Status.PENDING);
        existing.setMediaList(List.of(pendingMedia));
        when(mediaLocationRepository.findById(id)).thenReturn(Optional.of(existing));

        MediaLocationDeletionNotAllowedException exception = assertThrows(
                MediaLocationDeletionNotAllowedException.class,
                () -> mediaLocationService.deleteMediaLocation(id));

        assertEquals("Cannot delete media location while active, pending, or rejected media are assigned. Please delete those media first.", exception.getMessage());
        verify(mediaLocationRepository, never()).delete(any(MediaLocation.class));
    }

    @Test
    void deleteMediaLocation_WhenRejectedMediaAssigned_ThrowsMediaLocationDeletionNotAllowedException() {
        UUID id = UUID.randomUUID();
        MediaLocation existing = new MediaLocation();
        Media rejectedMedia = new Media();
        rejectedMedia.setStatus(Status.REJECTED);
        existing.setMediaList(List.of(rejectedMedia));
        when(mediaLocationRepository.findById(id)).thenReturn(Optional.of(existing));

        MediaLocationDeletionNotAllowedException exception = assertThrows(
                MediaLocationDeletionNotAllowedException.class,
                () -> mediaLocationService.deleteMediaLocation(id));

        assertEquals("Cannot delete media location while active, pending, or rejected media are assigned. Please delete those media first.", exception.getMessage());
        verify(mediaLocationRepository, never()).delete(any(MediaLocation.class));
    }
}
