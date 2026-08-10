package com.envisionad.webservice.media.PresentationLayer;

import com.envisionad.webservice.media.BusinessLayer.MediaService;
import com.envisionad.webservice.business.businesslogiclayer.BusinessService;
import com.envisionad.webservice.media.DataAccessLayer.Media;
import com.envisionad.webservice.media.DataAccessLayer.MediaLocation;
import com.envisionad.webservice.media.DataAccessLayer.Status;
import com.envisionad.webservice.media.DataAccessLayer.TypeOfDisplay;
import com.envisionad.webservice.media.MapperLayer.MediaRequestMapper;
import com.envisionad.webservice.media.MapperLayer.MediaResponseMapper;
import com.envisionad.webservice.media.PresentationLayer.Models.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaControllerUnitTest {

        @Mock
        private MediaService mediaService;

        @Mock
        private MediaRequestMapper requestMapper;

        @Mock
        private MediaResponseMapper responseMapper;

        @Mock
        private BusinessService businessService;

        @InjectMocks
        private MediaController mediaController;

        private Media media;
        private MediaResponseModel responseModel;
        private MediaRequestModel requestModel;
        private final String businessId = UUID.randomUUID().toString();
        private final UUID mediaLocationId = UUID.randomUUID();
        private final UUID mediaId = UUID.randomUUID();
        private final List<Double> bounds = Arrays.asList(-51.0, -50.0, 30.0, 31.0);
        private final List<Double> invalidBounds = Arrays.asList(-51.0, -50.0, 30.0);

    private Jwt mediaToken;

        @BeforeEach
        void setUp() {
                // Initialize JWT token, media location, schedule, and media request/response models used across tests

            mediaToken = createJwtToken("media-token", "auth0|696a89137cfdb558ea4a4a4a",
                    List.of("create:media", "update:media", "update:business", "read:employee",
                            "create:employee", "delete:employee", "read:verification", "create:verification"));


                MediaLocation mediaLocation = getMediaLocation();

                MediaLocationResponseModel mediaLocationResponseModel = getMediaLocationResponseModel();

                ScheduleModel schedule = new ScheduleModel();
                WeeklyScheduleEntry entry = new WeeklyScheduleEntry();
                entry.setDayOfWeek("Monday");
                entry.setActive(true);
                entry.setStartTime("09:00");
                entry.setEndTime("17:00");
                schedule.setWeeklySchedule(List.of(entry));

                media = new Media();
                media.setId(mediaId);
                media.setTitle("Test Media");
                media.setMediaOwnerName("Owner");
                media.setMediaLocation(mediaLocation);
                media.setTypeOfDisplay(TypeOfDisplay.DIGITAL);
                media.setPrice(new BigDecimal("100.00"));
                media.setStatus(Status.ACTIVE);
                media.setResolution("1920x1080");

                media.setLoopDuration(30);
                media.setSchedule(schedule);

                responseModel = new MediaResponseModel();
                responseModel.setId(mediaId);
                responseModel.setBusinessId(businessId);
                responseModel.setTitle("Test Media");
                responseModel.setMediaOwnerName("Owner");
                responseModel.setMediaLocation(mediaLocationResponseModel);
                responseModel.setTypeOfDisplay(TypeOfDisplay.DIGITAL);
                responseModel.setPrice(new BigDecimal("100.00"));
                responseModel.setStatus(Status.ACTIVE);
                responseModel.setResolution("1920x1080");

                responseModel.setLoopDuration(30);
                responseModel.setDailyImpressions(1000);
                responseModel.setSchedule(schedule);

                requestModel = new MediaRequestModel();
                requestModel.setTitle("Test Media");
                requestModel.setMediaOwnerName("Owner");
                requestModel.setMediaLocationId(mediaLocationId.toString());
                requestModel.setTypeOfDisplay(TypeOfDisplay.DIGITAL);
                requestModel.setPrice(new BigDecimal("100.00"));
                requestModel.setStatus(Status.ACTIVE);
                requestModel.setDailyImpressions(1000);
                requestModel.setResolution("1920x1080");

                requestModel.setLoopDuration(30);
                requestModel.setSchedule(schedule);
                requestModel.setImageUrl("http://example.com/image.jpg");
                requestModel.setPreviewConfiguration("{\"corners\": []}");
        }

    private Jwt createJwtToken(String tokenValue, String subject, List<String> permissions) {
        return Jwt.withTokenValue(tokenValue)
                .header("alg", "none")
                .claim("sub", subject)
                .claim("scope", "read write")
                .claim("permissions", permissions)
                .build();
    }

    private MediaLocationResponseModel getMediaLocationResponseModel() {
        MediaLocationResponseModel mediaLocationResponseModel = new MediaLocationResponseModel();
        mediaLocationResponseModel.setId(mediaLocationId);
        mediaLocationResponseModel.setName("Name");
        mediaLocationResponseModel.setCountry("Canada");
        mediaLocationResponseModel.setProvince("Quebec");
        mediaLocationResponseModel.setCity("Montreal");
        mediaLocationResponseModel.setStreet("Sesame Street 101");
        mediaLocationResponseModel.setPostalCode("J3G");
        mediaLocationResponseModel.setLatitude(30.5);
        mediaLocationResponseModel.setLongitude(-50.7);
        return mediaLocationResponseModel;
    }

        private MediaLocation getMediaLocation() {
                MediaLocation mediaLocation = new MediaLocation();
                mediaLocation.setId(mediaLocationId);
                mediaLocation.setName("Name");
                mediaLocation.setCountry("Canada");
                mediaLocation.setProvince("Quebec");
                mediaLocation.setCity("Montreal");
                mediaLocation.setStreet("Sesame Street 101");
                mediaLocation.setPostalCode("J3G");
                mediaLocation.setLatitude(30.5);
                mediaLocation.setLongitude(-50.7);

                return mediaLocation;
        }

        @Test
        void addMedia_WithValidJwt_ShouldSetBusinessId() {
                UUID businessId = UUID.randomUUID();
                String userId = "auth0|123";

                // Mock JWT
                Jwt jwt = mock(Jwt.class);
                when(jwt.getSubject()).thenReturn(userId);

                // Mock Business Service
                com.envisionad.webservice.business.presentationlayer.models.BusinessResponseModel businessResponse = new com.envisionad.webservice.business.presentationlayer.models.BusinessResponseModel();
                businessResponse.setBusinessId(businessId.toString());
                when(businessService.getBusinessByUserId(jwt, userId)).thenReturn(businessResponse);

                // Mock Mapper and Service
                when(requestMapper.requestModelToEntity(any(MediaRequestModel.class))).thenReturn(media);
                when(mediaService.addMedia(any(Media.class))).thenReturn(media);
                when(responseMapper.entityToResponseModel(any(Media.class))).thenReturn(responseModel);

                // Execute
                mediaController.addMedia(jwt, requestModel);

                // Verify
                assertEquals(businessId.toString(), requestModel.getBusinessId());
                verify(businessService).getBusinessByUserId(jwt, userId);
        }

        @Test
        void addMedia_WhenBusinessLookupThrows_ShouldStillCreateMedia() {
                String userId = "auth0|123";
                Jwt jwt = mock(Jwt.class);
                when(jwt.getSubject()).thenReturn(userId);

                when(businessService.getBusinessByUserId(jwt, userId))
                        .thenThrow(new RuntimeException("lookup failed"));
                when(requestMapper.requestModelToEntity(any(MediaRequestModel.class))).thenReturn(media);
                when(mediaService.addMedia(any(Media.class))).thenReturn(media);
                when(responseMapper.entityToResponseModel(any(Media.class))).thenReturn(responseModel);

                ResponseEntity<MediaResponseModel> response = mediaController.addMedia(jwt, requestModel);

                assertEquals(HttpStatus.CREATED, response.getStatusCode());
                assertNotNull(response.getBody());
                assertNull(requestModel.getBusinessId());
                verify(businessService).getBusinessByUserId(jwt, userId);
                verify(requestMapper).requestModelToEntity(any(MediaRequestModel.class));
                verify(mediaService).addMedia(any(Media.class));
        }

        @Test
        void addMedia_WhenBusinessResponseHasNullBusinessId_ShouldNotSetBusinessId() {
                String userId = "auth0|123";
                Jwt jwt = mock(Jwt.class);
                when(jwt.getSubject()).thenReturn(userId);

                com.envisionad.webservice.business.presentationlayer.models.BusinessResponseModel businessResponse = new com.envisionad.webservice.business.presentationlayer.models.BusinessResponseModel();
                businessResponse.setBusinessId(null);
                when(businessService.getBusinessByUserId(jwt, userId)).thenReturn(businessResponse);

                when(requestMapper.requestModelToEntity(any(MediaRequestModel.class))).thenReturn(media);
                when(mediaService.addMedia(any(Media.class))).thenReturn(media);
                when(responseMapper.entityToResponseModel(any(Media.class))).thenReturn(responseModel);

                ResponseEntity<MediaResponseModel> response = mediaController.addMedia(jwt, requestModel);

                assertEquals(HttpStatus.CREATED, response.getStatusCode());
                assertNotNull(response.getBody());
                assertNull(requestModel.getBusinessId());
                verify(businessService).getBusinessByUserId(jwt, userId);
                verify(requestMapper).requestModelToEntity(any(MediaRequestModel.class));
                verify(mediaService).addMedia(any(Media.class));
        }

        @Test
        void addMedia_ResolutionExceedsLimit_ShouldThrowException() {
                requestModel.setResolution("A".repeat(21));
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                        mediaController.addMedia(null, requestModel);
                });
                assertEquals("Resolution cannot exceed 20 characters", exception.getMessage());
        }

        @Test
        void addMedia_PriceExceedsLimit_ShouldThrowException() {
                requestModel.setPrice(new BigDecimal("100000.00"));
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                        mediaController.addMedia(null, requestModel);
                });
                assertEquals("Price cannot exceed 99999.99", exception.getMessage());
        }

        @Test
        void getAllMedia_ShouldReturnListOfMedia() {
                List<Media> mediaList = Arrays.asList(media);
                List<MediaResponseModel> responseList = Arrays.asList(responseModel);

                when(mediaService.getAllMedia()).thenReturn(mediaList);
                when(responseMapper.entityListToResponseModelList(mediaList)).thenReturn(responseList);

                List<MediaResponseModel> response = mediaController.getAllMedia();

                assertNotNull(response);
                assertEquals(1, response.size());
                assertEquals(mediaId, response.get(0).getId());
                assertEquals("Test Media", response.get(0).getTitle());
                verify(mediaService, times(1)).getAllMedia();
                verify(responseMapper, times(1)).entityListToResponseModelList(mediaList);
        }

        @Test
        void getAllFilteredActiveMedia_NoFilters_ShouldReturnPage() {
                Pageable pageable = PageRequest.of(0, 10);
                Page<Media> mediaPage = new PageImpl<>(List.of(media));
                Page<MediaResponseModel> responsePage = new PageImpl<>(List.of(responseModel));

                when(mediaService.getAllFilteredActiveMedia(pageable, null, null, null, null, null, null, null, null,
                                null, null, null))
                                .thenReturn(mediaPage);
                when(responseMapper.entityToResponseModel(media))
                                .thenReturn(responseModel);

                ResponseEntity<?> response = mediaController.getAllFilteredActiveMedia(pageable, null, null, null, null,
                                null,
                                null, null, null, null, null, null);

                assertEquals(HttpStatus.OK, response.getStatusCode());
                Page<?> body = (Page<?>) response.getBody();
                assertEquals(1, body.getTotalElements());

                verify(mediaService).getAllFilteredActiveMedia(pageable, null, null, null, null, null, null, null, null,
                                null, null, null);
        }

        @Test
        void getAllFilteredActiveMedia_MultipleFilters_ShouldReturnFiltered() {
                Pageable pageable = PageRequest.of(0, 10);
                Page<Media> mediaPage = new PageImpl<>(List.of(media));

                when(mediaService.getAllFilteredActiveMedia(
                                pageable,
                                "Billboard",
                                businessId,
                                BigDecimal.valueOf(50),
                                BigDecimal.valueOf(200),
                                1000,
                                "nearest",
                                50.0,
                                50.0,
                                bounds,
                                mediaId.toString(),
                                null))
                                .thenReturn(mediaPage);

                when(responseMapper.entityToResponseModel(media))
                                .thenReturn(responseModel);

                ResponseEntity<?> response = mediaController.getAllFilteredActiveMedia(
                                pageable,
                                "Billboard",
                                businessId,
                                BigDecimal.valueOf(50),
                                BigDecimal.valueOf(200),
                                1000,
                                "nearest",
                                50.0,
                                50.0,
                                bounds,
                                mediaId.toString(),
                                null);

                assertEquals(HttpStatus.OK, response.getStatusCode());
                Page<?> body = (Page<?>) response.getBody();
                assertEquals(1, body.getTotalElements());
        }

        @Test
        void getAllFilteredActiveMedia_TitleOnly_ShouldReturnFiltered() {
                Pageable pageable = PageRequest.of(0, 10);
                Page<Media> mediaPage = new PageImpl<>(List.of(media));

                when(mediaService.getAllFilteredActiveMedia(pageable, "Test", null, null, null, null, null, null, null,
                                null, null, null))
                                .thenReturn(mediaPage);
                when(responseMapper.entityToResponseModel(media))
                                .thenReturn(responseModel);

                ResponseEntity<?> response = mediaController.getAllFilteredActiveMedia(pageable, "Test", null, null,
                                null,
                                null, null, null, null, null, null, null);

                assertEquals(HttpStatus.OK, response.getStatusCode());
                Page<?> body = (Page<?>) response.getBody();
                assertEquals(1, body.getContent().size());
        }

        @Test
        void getAllFilteredActiveMedia_TitleNoResults_ShouldReturnEmptyPage() {
                Pageable pageable = PageRequest.of(0, 10);
                Page<Media> emptyPage = Page.empty();

                when(mediaService.getAllFilteredActiveMedia(pageable, "NoMatch", null, null, null, null, null, null,
                                null, null, null, null))
                                .thenReturn(emptyPage);

                ResponseEntity<?> response = mediaController.getAllFilteredActiveMedia(pageable, "NoMatch", null, null,
                                null,
                                null, null, null, null, null, null, null);

                assertEquals(HttpStatus.OK, response.getStatusCode());
                Page<?> body = (Page<?>) response.getBody();
                assertTrue(body.isEmpty());
        }

        @Test
        void getAllFilteredActiveMedia_BusinessIdOnly_ShouldFilter() {
                Pageable pageable = PageRequest.of(0, 10);
                Page<Media> mediaPage = new PageImpl<>(List.of(media));

        when(mediaService.getAllFilteredActiveMedia(
                pageable,
                null,
                businessId,
                null, null, null,
                null, null, null,
                null, null, null
        )).thenReturn(mediaPage);

                when(responseMapper.entityToResponseModel(media)).thenReturn(responseModel);

                ResponseEntity<?> response = mediaController.getAllFilteredActiveMedia(
                                pageable,
                                null,
                                businessId,
                                null, null, null,
                                null, null, null,
                                null, null, null);

                assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        void getAllFilteredActiveMedia_MinPriceNegative_ShouldThrowException() {
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                        mediaController.getAllFilteredActiveMedia(
                                        null,
                                        null,
                                        null,
                                        BigDecimal.valueOf(-1),
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null);
                });

                assertEquals("minPrice must be non-negative.", exception.getMessage());
                verifyNoInteractions(mediaService);
        }

        @Test
        void getAllFilteredActiveMedia_MaxPriceNegative_ShouldThrowException() {
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                        mediaController.getAllFilteredActiveMedia(
                                        null,
                                        null,
                                        null,
                                        null,
                                        BigDecimal.valueOf(-5),
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null);
                });

                assertEquals("maxPrice must be non-negative.", exception.getMessage());
                verifyNoInteractions(mediaService);
        }

        @Test
        void getAllFilteredActiveMedia_MinGreaterThanMax_ShouldThrowException() {
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                        mediaController.getAllFilteredActiveMedia(
                                        null,
                                        null,
                                        null,
                                        BigDecimal.valueOf(50),
                                        BigDecimal.valueOf(10),
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null);
                });

                assertEquals("minPrice must not be greater than maxPrice.", exception.getMessage());
                verifyNoInteractions(mediaService);
        }

        @Test
        void getAllFilteredActiveMedia_MinWeeklyImpressionsNegative_ShouldThrowException() {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                mediaController.getAllFilteredActiveMedia(
                        null,
                        null,
                        null,
                        null,
                        null,
                        -10,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
            });

            assertEquals("minWeeklyImpressions must be non-negative.", exception.getMessage());
            verifyNoInteractions(mediaService);
        }

        @Test
        void getAllFilteredActiveMedia_SpecialSortOnly_ShouldPassThrough() {
                Pageable pageable = PageRequest.of(0, 10);
                Page<Media> mediaPage = new PageImpl<>(List.of(media));

                when(mediaService.getAllFilteredActiveMedia(
                                pageable,
                                null, null, null, null, null,
                                "nearest",
                                50.0, 50.0,
                                null, null, null)).thenReturn(mediaPage);

                when(responseMapper.entityToResponseModel(media)).thenReturn(responseModel);

                ResponseEntity<?> response = mediaController.getAllFilteredActiveMedia(
                                pageable,
                                null, null, null, null, null,
                                "nearest",
                                50.0, 50.0,
                                null, null, null);

                assertEquals(HttpStatus.OK, response.getStatusCode());
        }

    @Test
    void getAllFilteredActiveMedia_WeeklyImpressionsAsc_ShouldPassThrough() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Media> mediaPage = new PageImpl<>(List.of(media));

        when(mediaService.getAllFilteredActiveMedia(
                pageable,
                null, null, null, null,
                null,
                "weeklyImpressions,asc",
                null, null,
                null, null, null
        )).thenReturn(mediaPage);

        when(responseMapper.entityToResponseModel(media))
                .thenReturn(responseModel);

        ResponseEntity<?> response = mediaController.getAllFilteredActiveMedia(
                pageable,
                null, null, null, null,
                null,
                "weeklyImpressions,asc",
                null, null,
                null, null, null
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());

        verify(mediaService).getAllFilteredActiveMedia(
                pageable,
                null, null, null, null,
                null,
                "weeklyImpressions,asc",
                null, null,
                null, null, null
        );
    }

    @Test
    void getAllFilteredActiveMedia_WeeklyImpressionsDesc_ShouldPassThrough() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Media> mediaPage = new PageImpl<>(List.of(media));

        when(mediaService.getAllFilteredActiveMedia(
                pageable,
                null, null, null, null,
                null,
                "weeklyImpressions,desc",
                null, null,
                null, null, null
        )).thenReturn(mediaPage);

        when(responseMapper.entityToResponseModel(media))
                .thenReturn(responseModel);

        ResponseEntity<?> response = mediaController.getAllFilteredActiveMedia(
                pageable,
                null, null, null, null,
                null,
                "weeklyImpressions,desc",
                null, null,
                null, null, null
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
        void getAllFilteredActiveMedia_BoundsNotFour_ShouldThrowException() {
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                        mediaController.getAllFilteredActiveMedia(
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        invalidBounds,
                                        null,
                                        null);
                });

                assertEquals("bounds must have a length of exactly 4.", exception.getMessage());
                verifyNoInteractions(mediaService);
        }

        @Test
        void getAllFilteredActiveMedia_ExcludedIdOnly_ShouldFilter() {
                Pageable pageable = PageRequest.of(0, 10);
                Page<Media> mediaPage = new PageImpl<>(List.of(media));

                when(mediaService.getAllFilteredActiveMedia(
                                pageable,
                                null, null, null, null, null,
                                null, null, null,
                                null, mediaId.toString(), null)).thenReturn(mediaPage);

                when(responseMapper.entityToResponseModel(media)).thenReturn(responseModel);

                ResponseEntity<?> response = mediaController.getAllFilteredActiveMedia(
                                pageable,
                                null, null, null, null, null,
                                null, null, null,
                                null, mediaId.toString(), null);

                assertEquals(HttpStatus.OK, response.getStatusCode());
                verify(mediaService).getAllFilteredActiveMedia(
                                pageable,
                                null, null, null, null, null,
                                null, null, null,
                                null, mediaId.toString(), null);
        }

        @Test
        void getMediaById_WhenFound_ShouldReturnMedia() {
                when(mediaService.getMediaById(mediaId)).thenReturn(media);
                when(responseMapper.entityToResponseModel(media)).thenReturn(responseModel);

                ResponseEntity<MediaResponseModel> response = mediaController.getMediaById(String.valueOf(mediaId));

                assertNotNull(response);
                assertEquals(HttpStatus.OK, response.getStatusCode());
                assertNotNull(response.getBody());
                assertEquals(mediaId, response.getBody().getId());
                verify(mediaService, times(1)).getMediaById(mediaId);
                verify(responseMapper, times(1)).entityToResponseModel(media);
        }

        @Test
        void getMediaById_WhenNotFound_ShouldReturn404() {
                when(mediaService.getMediaById(mediaId)).thenReturn(null);

                ResponseEntity<MediaResponseModel> response = mediaController.getMediaById(String.valueOf(mediaId));

                assertNotNull(response);
                assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
                assertNull(response.getBody());
                verify(mediaService, times(1)).getMediaById(mediaId);
        }

        @Test
        void addMedia_ShouldReturnCreatedMedia() {
                when(requestMapper.requestModelToEntity(any(MediaRequestModel.class))).thenReturn(media);
                when(mediaService.addMedia(any(Media.class))).thenReturn(media);
                when(responseMapper.entityToResponseModel(any(Media.class))).thenReturn(responseModel);

                ResponseEntity<MediaResponseModel> response = mediaController.addMedia(null, requestModel);

                assertNotNull(response);
                assertEquals(HttpStatus.CREATED, response.getStatusCode());
                assertNotNull(response.getBody());
                assertEquals(mediaId, response.getBody().getId());
                verify(requestMapper, times(1)).requestModelToEntity(any(MediaRequestModel.class));
                verify(mediaService, times(1)).addMedia(any(Media.class));
                verify(responseMapper, times(1)).entityToResponseModel(any(Media.class));
        }

        @Test
        void updateMedia_ShouldReturnUpdatedMedia() {
                when(mediaService.updateMediaById(mediaToken, String.valueOf(mediaId), requestModel)).thenReturn(responseModel);
                ResponseEntity<MediaResponseModel> response = mediaController.updateMedia(
                        mediaToken,
                        String.valueOf(mediaId),
                        requestModel);

                assertNotNull(response);
                assertEquals(HttpStatus.OK, response.getStatusCode());
                assertNotNull(response.getBody());
                assertEquals(mediaId, response.getBody().getId());
                verify(mediaService, times(1)).updateMediaById(mediaToken, String.valueOf(mediaId), requestModel);
        }

        @Test
        void deleteMedia_ShouldReturnNoContent() {
                ResponseEntity<Void> response = mediaController.deleteMedia(mediaToken, String.valueOf(mediaId));

                assertNotNull(response);
                assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
                assertNull(response.getBody());
                verify(mediaService, times(1)).deleteMedia(mediaToken, mediaId);
        }
}
