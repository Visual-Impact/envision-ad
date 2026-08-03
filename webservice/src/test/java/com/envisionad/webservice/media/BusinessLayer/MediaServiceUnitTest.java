package com.envisionad.webservice.media.BusinessLayer;

import com.cloudinary.Cloudinary;
import com.envisionad.webservice.business.dataaccesslayer.Business;
import com.envisionad.webservice.business.dataaccesslayer.BusinessIdentifier;
import com.envisionad.webservice.media.DataAccessLayer.Media;
import com.envisionad.webservice.media.DataAccessLayer.MediaLocation;
import com.envisionad.webservice.media.DataAccessLayer.MediaRepository;
import com.envisionad.webservice.media.DataAccessLayer.Status;
import com.envisionad.webservice.media.DataAccessLayer.TypeOfDisplay;
import com.envisionad.webservice.media.MapperLayer.MediaResponseMapper;
import com.envisionad.webservice.media.PresentationLayer.Models.MediaRequestModel;
import com.envisionad.webservice.media.PresentationLayer.Models.MediaStatusPatchRequestModel;
import com.envisionad.webservice.media.PresentationLayer.Models.ScheduleModel;
import com.envisionad.webservice.media.PresentationLayer.Models.WeeklyScheduleEntry;
import com.envisionad.webservice.media.exceptions.InvalidMediaStatusTransitionException;
import com.envisionad.webservice.media.exceptions.MediaRejectedReactivationException;
import com.envisionad.webservice.utils.JwtUtils;
import com.envisionad.webservice.utils.MathFunctions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaServiceUnitTest {

        @InjectMocks
        private MediaServiceImpl mediaService;

        @Mock
        private MediaRepository mediaRepository;

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private com.cloudinary.Uploader uploader;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private MediaResponseMapper mediaResponseMapper;

    @Mock
    private Jwt mockJwt;

    private String business1Id;
    private String business2Id;
    private Business business1;
    private Business business2;

        private Media media1;
        private Media media2;
        private Media media3;
        private MediaLocation location1;
        private MediaLocation location2;
        private MediaLocation location3;

        @BeforeEach
        void setUp() {
                business1 = createBusiness(1);
                business2 = createBusiness(2);

                business1Id = business1.getBusinessId().getBusinessId();
                business2Id = business2.getBusinessId().getBusinessId();

        // Create MediaLocations with different coordinates
        location1 = createMediaLocation(
                "Downtown Billboard A",
                43.651070,  // Toronto downtown
                -79.347015
        );

                location2 = createMediaLocation(
                                "Midtown Billboard B",
                                43.700000, // North Toronto
                                -79.400000);

                location3 = createMediaLocation(
                                "Suburb Billboard C",
                                43.800000, // Far north
                                -79.500000);

                // Create Media with different properties
                media1 = createMedia(
                                "Premium Downtown Screen",
                                business1Id,
                                location1,
                                new BigDecimal("500.00"),
                                50000,
                                Status.ACTIVE);

                media2 = createMedia(
                                "Budget Midtown Screen",
                                business1Id,
                                location2,
                                new BigDecimal("200.00"),
                                20000,
                                Status.ACTIVE);

                media3 = createMedia(
                                "Economy Suburb Screen",
                                business2Id,
                                location3,
                                new BigDecimal("100.00"),
                                10000,
                                Status.ACTIVE
                );
            lenient().when(cloudinary.uploader()).thenReturn(uploader);
        }

        // ==================== getAllFilteredActiveMedia Tests ====================

        @Test
        void getAllFilteredActiveMedia_WithNoFilters_ShouldReturnAllActiveMedia() {
                // Arrange
                Pageable pageable = PageRequest.of(0, 10);
                when(mediaRepository.findAll(any(Specification.class), eq(pageable)))
                                .thenReturn(Page.empty());

                // Act
                Page<Media> result = mediaService.getAllFilteredActiveMedia(
                                pageable, null, null, null, null, null, null, null, null, null, null, null);

                // Assert
                assertNotNull(result);
                verify(mediaRepository, times(1)).findAll(any(Specification.class), eq(pageable));
        }

        @Test
        void getAllFilteredActiveMedia_WithTitleFilter_ShouldFilterByTitle() {
                // Arrange
                Pageable pageable = PageRequest.of(0, 10);
                String titleFilter = "Downtown";
                when(mediaRepository.findAll(any(Specification.class), eq(pageable)))
                                .thenReturn(Page.empty());

                // Act
                mediaService.getAllFilteredActiveMedia(
                                pageable, titleFilter, null, null, null, null, null, null, null, null, null, null);

                // Assert
                ArgumentCaptor<Specification<Media>> specCaptor = ArgumentCaptor.forClass(Specification.class);
                verify(mediaRepository).findAll(specCaptor.capture(), eq(pageable));
                assertNotNull(specCaptor.getValue());
        }

        @Test
        void getAllFilteredActiveMedia_WithPriceRange_ShouldFilterByPrice() {
                // Arrange
                Pageable pageable = PageRequest.of(0, 10);
                BigDecimal minPrice = new BigDecimal("100.00");
                BigDecimal maxPrice = new BigDecimal("300.00");
                when(mediaRepository.findAll(any(Specification.class), eq(pageable)))
                                .thenReturn(Page.empty());

                // Act
                mediaService.getAllFilteredActiveMedia(
                                pageable, null, null, minPrice, maxPrice, null, null, null, null, null, null, null);

                // Assert
                verify(mediaRepository).findAll(any(Specification.class), eq(pageable));
        }

        @Test
        void getAllFilteredActiveMedia_WithMinPriceOnly_ShouldFilterByMinPrice() {
                // Arrange
                Pageable pageable = PageRequest.of(0, 10);
                BigDecimal minPrice = new BigDecimal("200.00");
                when(mediaRepository.findAll(any(Specification.class), eq(pageable)))
                                .thenReturn(Page.empty());

                // Act
                mediaService.getAllFilteredActiveMedia(
                                pageable, null, null, minPrice, null, null, null, null, null, null, null, null);

                // Assert
                verify(mediaRepository).findAll(any(Specification.class), eq(pageable));
        }

        @Test
        void getAllFilteredActiveMedia_WithMaxPriceOnly_ShouldFilterByMaxPrice() {
                // Arrange
                Pageable pageable = PageRequest.of(0, 10);
                BigDecimal maxPrice = new BigDecimal("300.00");
                when(mediaRepository.findAll(any(Specification.class), eq(pageable)))
                                .thenReturn(Page.empty());

                // Act
                mediaService.getAllFilteredActiveMedia(
                                pageable, null, null, null, maxPrice, null, null, null, null, null, null, null);

                // Assert
                verify(mediaRepository).findAll(any(Specification.class), eq(pageable));
        }

        @Test
        void getAllFilteredActiveMedia_WithMinWeeklyImpressions_ShouldFilterByImpressions() {
                // Arrange
                Pageable pageable = PageRequest.of(0, 10);
                Integer minImpressions = 25000;
                when(mediaRepository.findAll(any(Specification.class), eq(pageable)))
                                .thenReturn(Page.empty());

                // Act
                mediaService.getAllFilteredActiveMedia(
                                pageable, null, null, null, null, minImpressions, null, null, null, null, null, null);

                // Assert
                verify(mediaRepository).findAll(any(Specification.class), eq(pageable));
        }

        @Test
        void getAllFilteredActiveMedia_WithBounds_ShouldFilterByBounds() {
                // Arrange
                Pageable pageable = PageRequest.of(0, 10);
                List<Double> bounds = Arrays.asList(-51.0, -50.0, 30.0, 31.0);
                when(mediaRepository.findAll(any(Specification.class), eq(pageable)))
                                .thenReturn(Page.empty());

                // Act
                mediaService.getAllFilteredActiveMedia(
                                pageable, null, null, null, null, null, null, null, null, bounds, null, null);

                // Assert - Should use regular pagination
                verify(mediaRepository).findAll(any(Specification.class), eq(pageable));
        }

        @Test
        void getAllFilteredActiveMedia_WithAllFilters_ShouldApplyAllFilters() {
                // Arrange
                Pageable pageable = PageRequest.of(0, 10);
                String title = "Screen";
                BigDecimal minPrice = new BigDecimal("100.00");
                BigDecimal maxPrice = new BigDecimal("500.00");
                Integer minImpressions = 15000;
                List<Double> bounds = Arrays.asList(-51.0, -50.0, 30.0, 31.0);
                when(mediaRepository.findAll(any(Specification.class), eq(pageable)))
                                .thenReturn(Page.empty());

                // Act
                mediaService.getAllFilteredActiveMedia(
                                pageable, title, business1Id, minPrice, maxPrice, minImpressions, null, null, null,
                                bounds, media2.getId().toString(), null);

                // Assert
                verify(mediaRepository).findAll(any(Specification.class), eq(pageable));
        }

        @Test
        void getAllFilteredActiveMedia_WithNearestSort_ShouldSortByDistance() {
                // Arrange
                Pageable pageable = PageRequest.of(0, 10);
                Double userLat = 43.651070;
                Double userLng = -79.347015;
                List<Media> mediaList = new ArrayList<>(List.of(media3, media2, media1)); // Mutable list

                when(mediaRepository.findAll(any(Specification.class)))
                                .thenReturn(mediaList);

                // Act
                Page<Media> result = mediaService.getAllFilteredActiveMedia(
                                pageable, null, null, null, null, null, "nearest", userLat, userLng, null, null, null);

                // Assert
                assertNotNull(result);
                List<Media> content = result.getContent();
                // First should be media1 (closest to user location which is same as location1)
                assertEquals("Premium Downtown Screen", content.get(0).getTitle());
                verify(mediaRepository).findAll(any(Specification.class));
        }

        @Test
        void getAllFilteredActiveMedia_WithNearestSort_AndPagination_ShouldReturnCorrectPage() {
                // Arrange
                Pageable pageable = PageRequest.of(1, 1); // Second page, 1 item per page
                Double userLat = 43.651070;
                Double userLng = -79.347015;
                List<Media> mediaList = new ArrayList<>(List.of(media1, media2, media3));

                when(mediaRepository.findAll(any(Specification.class)))
                                .thenReturn(mediaList);

                // Act
                Page<Media> result = mediaService.getAllFilteredActiveMedia(
                                pageable, null, null, null, null, null, "nearest", userLat, userLng, null, null, null);

                // Assert
                assertNotNull(result);
                assertEquals(1, result.getContent().size());
                assertEquals(3, result.getTotalElements());
                assertEquals(3, result.getTotalPages());
        }

        @Test
        void getAllFilteredActiveMedia_WithNearestSort_AndEmptyList_ShouldReturnEmptyPage() {
                // Arrange
                Pageable pageable = PageRequest.of(0, 10);
                Double userLat = 43.651070;
                Double userLng = -79.347015;
                List<Media> mediaList = new ArrayList<>();

                when(mediaRepository.findAll(any(Specification.class)))
                                .thenReturn(mediaList);

                // Act
                Page<Media> result = mediaService.getAllFilteredActiveMedia(
                                pageable, null, null, null, null, null, "nearest", userLat, userLng, null, null, null);

                // Assert
                assertNotNull(result);
                assertTrue(result.isEmpty());
        }

        @Test
        void getAllFilteredActiveMedia_WithNearestSort_AndPageBeyondSize_ShouldReturnEmptyPage() {
                // Arrange
                Pageable pageable = PageRequest.of(10, 10); // Way beyond available data
                Double userLat = 43.651070;
                Double userLng = -79.347015;
                List<Media> mediaList = new ArrayList<>(List.of(media1, media2));

                when(mediaRepository.findAll(any(Specification.class)))
                                .thenReturn(mediaList);

                // Act
                Page<Media> result = mediaService.getAllFilteredActiveMedia(
                                pageable, null, null, null, null, null, "nearest", userLat, userLng, null, null, null);

                // Assert
                assertNotNull(result);
                assertTrue(result.isEmpty());
        }

        @Test
        void getAllFilteredActiveMedia_WithNearestSort_MediaWithNullLocation_ShouldBeSortedLast() {
                // Arrange
                Pageable pageable = PageRequest.of(0, 10);
                Double userLat = 43.651070;
                Double userLng = -79.347015;

                Media mediaWithNullLocation = createMedia("No Location Media", business1Id, null,
                                new BigDecimal("150.00"), 15000, Status.ACTIVE);

                List<Media> mediaList = new ArrayList<>(List.of(mediaWithNullLocation, media2, media1));

                when(mediaRepository.findAll(any(Specification.class)))
                                .thenReturn(mediaList);

                // Act
                Page<Media> result = mediaService.getAllFilteredActiveMedia(
                                pageable, null, null, null, null, null, "nearest", userLat, userLng, null, null, null);

                // Assert
                assertNotNull(result);
                List<Media> content = result.getContent();
                // Media with null location should be last
                assertEquals("No Location Media", content.get(content.size() - 1).getTitle());
        }

        @Test
        void getAllFilteredActiveMedia_WithNearestSort_MissingUserCoordinates_ShouldNotSortByDistance() {
                // Arrange
                Pageable pageable = PageRequest.of(0, 10);
                when(mediaRepository.findAll(any(Specification.class), eq(pageable)))
                                .thenReturn(Page.empty());

                // Act - Missing userLat
                mediaService.getAllFilteredActiveMedia(
                                pageable, null, null, null, null, null, "nearest", null, -79.347015, null, null, null);

                // Assert - Should use regular pagination instead of distance sorting
                verify(mediaRepository).findAll(any(Specification.class), eq(pageable));
        }

        @Test
        void getAllFilteredActiveMedia_WithNearestSort_MissingUserLongitude_ShouldNotSortByDistance() {
                // Arrange
                Pageable pageable = PageRequest.of(0, 10);
                when(mediaRepository.findAll(any(Specification.class), eq(pageable)))
                                .thenReturn(Page.empty());

                // Act - Missing userLng
                mediaService.getAllFilteredActiveMedia(
                                pageable, null, null, null, null, null, "nearest", 43.651070, null, null, null, null);

                // Assert - Should use regular pagination instead of distance sorting
                verify(mediaRepository).findAll(any(Specification.class), eq(pageable));
        }

        @Test
        void getAllFilteredActiveMedia_WithDifferentSpecialSort_ShouldNotSortByDistance() {
                // Arrange
                Pageable pageable = PageRequest.of(0, 10);
                when(mediaRepository.findAll(any(Specification.class), eq(pageable)))
                                .thenReturn(Page.empty());

                // Act
                mediaService.getAllFilteredActiveMedia(
                                pageable, null, null, null, null, null, "price", 43.651070, -79.347015, null, null, null);

                // Assert - Should use regular pagination
                verify(mediaRepository).findAll(any(Specification.class), eq(pageable));
        }

    @Test
    void getAllFilteredActiveMedia_WithWeeklyImpressionsAsc_ShouldSortCorrectly() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);

        media1.setDailyImpressions(60);
        media1.setActiveDays(7);

        media2.setDailyImpressions(70);
        media2.setActiveDays(2);

        List<Media> mediaList = new ArrayList<>(List.of(media1, media2));

        when(mediaRepository.findAll(any(Specification.class)))
                .thenReturn(mediaList);

        // Act
        Page<Media> result = mediaService.getAllFilteredActiveMedia(
                pageable, null, null, null, null,
                null, "weeklyImpressions,asc",
                null, null, null, null
        , null);

        // Assert
        List<Media> content = result.getContent();
        assertEquals(media2.getTitle(), content.get(0).getTitle()); // 140 first
        assertEquals(media1.getTitle(), content.get(1).getTitle()); // 420 second
    }

    @Test
    void getAllFilteredActiveMedia_WithWeeklyImpressionsDesc_ShouldSortCorrectly() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);

        media1.setDailyImpressions(60);
        media1.setActiveDays(7);   // 420

        media2.setDailyImpressions(70);
        media2.setActiveDays(2);   // 140

        List<Media> mediaList = new ArrayList<>(List.of(media2, media1));

        when(mediaRepository.findAll(any(Specification.class)))
                .thenReturn(mediaList);

        // Act
        Page<Media> result = mediaService.getAllFilteredActiveMedia(
                pageable, null, null, null, null,
                null, "weeklyImpressions,desc",
                null, null, null, null
        , null);

        // Assert
        List<Media> content = result.getContent();
        assertEquals(media1.getTitle(), content.get(0).getTitle()); // 420 first
        assertEquals(media2.getTitle(), content.get(1).getTitle()); // 140 second
    }

    @Test
    void getAllFilteredActiveMedia_WithWeeklyImpressionsDesc_AndPagination_ShouldReturnCorrectPage() {
        // Arrange
        Pageable pageable = PageRequest.of(1, 1); // second page, 1 per page

        media1.setDailyImpressions(60);
        media1.setActiveDays(7);   // 420

        media2.setDailyImpressions(70);
        media2.setActiveDays(2);   // 140

        List<Media> mediaList = new ArrayList<>(List.of(media1, media2));

        when(mediaRepository.findAll(any(Specification.class)))
                .thenReturn(mediaList);

        // Act
        Page<Media> result = mediaService.getAllFilteredActiveMedia(
                pageable, null, null, null, null,
                null, "weeklyImpressions,desc",
                null, null, null, null
        , null);

        // Assert
        assertEquals(1, result.getContent().size());
        assertEquals(2, result.getTotalElements());
    }

    @Test
    void getAllFilteredActiveMedia_WithWeeklyImpressionsAndNullValues_ShouldHandleGracefully() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);

        media1.setDailyImpressions(null);
        media1.setActiveDays(7);

        media2.setDailyImpressions(50);
        media2.setActiveDays(null);

        List<Media> mediaList = new ArrayList<>(List.of(media1, media2));

        when(mediaRepository.findAll(any(Specification.class)))
                .thenReturn(mediaList);

        // Act
        Page<Media> result = mediaService.getAllFilteredActiveMedia(
                pageable, null, null, null, null,
                null, "weeklyImpressions,asc",
                null, null, null, null
        , null);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.getContent().size());
    }



    // ==================== Distance Calculation Tests ====================

        @Test
        void distance_SameLocation_ShouldReturnZero() {
                // Arrange
                double lat = 43.651070;
                double lon = -79.347015;

                // Act - Using reflection to test private method
                double distance = MathFunctions.distance(lat, lon, lat, lon);

                // Assert
                assertEquals(0.0, distance, 0.001);
        }

        @Test
        void distance_TorontoToNewYork_ShouldReturnCorrectDistance() {
                // Arrange
                double torontoLat = 43.651070;
                double torontoLon = -79.347015;
                double newYorkLat = 40.712776;
                double newYorkLon = -74.005974;

                // Act
                double distance = MathFunctions.distance(torontoLat, torontoLon, newYorkLat, newYorkLon);

                // Assert - Distance between Toronto and New York is approximately 550 km
                assertTrue(distance > 500 && distance < 600,
                                "Distance should be approximately 550 km, but was: " + distance);
        }

        @Test
        void distance_AcrossEquator_ShouldCalculateCorrectly() {
                // Arrange - One in northern hemisphere, one in southern
                double lat1 = 43.651070;
                double lon1 = -79.347015;
                double lat2 = -33.868820; // Sydney, Australia
                double lon2 = 151.209290;

                // Act
                double distance = MathFunctions.distance(lat1, lon1, lat2, lon2);

                // Assert - Should be a very large distance (around 15,000+ km)
                assertTrue(distance > 15000, "Distance should be over 15,000 km");
        }

        @Test
        void distance_ShortDistance_ShouldBeAccurate() {
                // Arrange - Two points very close together in Toronto
                double lat1 = 43.651070;
                double lon1 = -79.347015;
                double lat2 = 43.652070; // About 1 km north
                double lon2 = -79.347015;

                // Act
                double distance = MathFunctions.distance(lat1, lon1, lat2, lon2);

                // Assert - Should be approximately 0.11 km
                assertTrue(distance < 1.0, "Distance should be less than 1 km");
                assertTrue(distance > 0.0, "Distance should be greater than 0");
        }

        @Test
        void distance_AntipodePoints_ShouldReturnMaxDistance() {
                // Arrange - Opposite sides of the earth
                double lat1 = 45.0;
                double lon1 = 0.0;
                double lat2 = -45.0;
                double lon2 = 180.0;

                // Act
                double distance = MathFunctions.distance(lat1, lon1, lat2, lon2);

                // Assert - Should be close to half the earth's circumference
                assertTrue(distance > 15000 && distance < 20100,
                                "Distance should be between 15,000 and 20,100 km");
        }

        @Test
        void distance_WithNegativeCoordinates_ShouldCalculateCorrectly() {
                // Arrange
                double lat1 = -33.868820; // Sydney
                double lon1 = 151.209290;
                double lat2 = -34.603490; // Buenos Aires
                double lon2 = -58.381560;

                // Act
                double distance = MathFunctions.distance(lat1, lon1, lat2, lon2);

                // Assert - Should be a large distance
                assertTrue(distance > 11000, "Distance should be over 11,000 km");
        }

        // ==================== Lambda Function Tests (Implicit) ====================

        @Test
        void lambdaSort_WithMultipleMedia_ShouldSortByDistanceAscending() {
                // Arrange
                Pageable pageable = PageRequest.of(0, 10);
                Double userLat = 43.651070;
                Double userLng = -79.347015;

                // Create media at different distances
                List<Media> mediaList = new ArrayList<>(List.of(media3, media1, media2));

                when(mediaRepository.findAll(any(Specification.class)))
                                .thenReturn(mediaList);

                // Act
                Page<Media> result = mediaService.getAllFilteredActiveMedia(
                                pageable, null, null, null, null, null, "nearest", userLat, userLng, null, null, null);

                // Assert - Should be sorted: media1 (closest), media2 (medium), media3
                // (farthest)
                List<Media> content = result.getContent();
                assertEquals(3, content.size());
                assertEquals(media1.getTitle(), content.get(0).getTitle());
                assertEquals(media2.getTitle(), content.get(1).getTitle());
                assertEquals(media3.getTitle(), content.get(2).getTitle());
        }

        @Test
        void lambdaSort_WithIdenticalDistances_ShouldMaintainStableSort() {
                // Arrange
                Pageable pageable = PageRequest.of(0, 10);
                Double userLat = 43.651070;
                Double userLng = -79.347015;

                // Create multiple media at same location
                Media media4 = createMedia("Same Location 1", business1Id, location1,
                                new BigDecimal("100.00"), 10000, Status.ACTIVE);
                Media media5 = createMedia("Same Location 2", business1Id, location1,
                                new BigDecimal("200.00"), 20000, Status.ACTIVE);

                List<Media> mediaList = new ArrayList<>(List.of(media4, media5));

                when(mediaRepository.findAll(any(Specification.class)))
                                .thenReturn(mediaList);

                // Act
                Page<Media> result = mediaService.getAllFilteredActiveMedia(
                                pageable, null, null, null, null, null, "nearest", userLat, userLng, null, null, null);

                // Assert - Both should be returned as they're at the same distance
                assertEquals(2, result.getContent().size());
        }

    // ==================== CRUD & Cloudinary Tests ====================


    @Test
    void deleteMedia_ShouldTriggerCloudinaryDeletion() throws Exception {
        // Arrange
        UUID id = UUID.randomUUID();
        media1.setImageUrl("https://res.cloudinary.com/demo/image/upload/v1/sample.png");
        when(mediaRepository.findById(id)).thenReturn(Optional.of(media1));

        // Act
        mediaService.deleteMedia(id);

        // Assert
        verify(uploader).destroy(eq("sample"), anyMap());
        verify(mediaRepository).delete(media1);
    }

    @Test
    void updateMedia_WhenImageChanges_ShouldDeleteOldImage() throws Exception {
        // Arrange
        UUID id = media1.getId();
        media1.setImageUrl(".../upload/old_id.png");

        MediaRequestModel requestModel = new MediaRequestModel();
        requestModel.setTitle("Updated Title");
        requestModel.setPrice(media1.getPrice());
        requestModel.setDailyImpressions(media1.getDailyImpressions());
        requestModel.setTypeOfDisplay(TypeOfDisplay.DIGITAL);
        requestModel.setLoopDuration(10);
        requestModel.setResolution("1920x1080");

        requestModel.setSchedule(media1.getSchedule());
        requestModel.setImageUrl("https://res.cloudinary.com/demo/image/upload/v1/sample.png");
        requestModel.setPreviewConfiguration("{\"topLeft\":{\"x\":0,\"y\":0},\"topRight\":{\"x\":100,\"y\":0},\"bottomLeft\":{\"x\":0,\"y\":100},\"bottomRight\":{\"x\":100,\"y\":100}}");
        requestModel.setMediaLocationId(media1.getMediaLocation().getId().toString());

        when(mediaRepository.findById(id)).thenReturn(Optional.of(media1));
        when(mediaRepository.save(any(Media.class))).thenReturn(media1);
        doNothing().when(jwtUtils).validateUserIsEmployeeOfBusiness(any(Jwt.class), anyString());

        // Act
        mediaService.updateMediaById(mockJwt, id.toString(), requestModel);

        // Assert
        verify(uploader).destroy(eq("old_id"), anyMap());
    }

    @Test
    void updateMedia_WhenImageDoesNotChange_ShouldNotCallCloudinary() throws Exception {
        // Arrange
        UUID id = media1.getId();
        String sameUrl = "https://res.cloudinary.com/demo/image/upload/v1/sample.png";
        media1.setImageUrl(sameUrl);

        MediaRequestModel requestModel = new MediaRequestModel();
        requestModel.setTitle("Updated Title");
        requestModel.setPrice(new BigDecimal("500.00"));
        requestModel.setDailyImpressions(50000);
        requestModel.setTypeOfDisplay(TypeOfDisplay.DIGITAL);
        requestModel.setLoopDuration(10);
        requestModel.setResolution("1920x1080");

        requestModel.setSchedule(media1.getSchedule());
        requestModel.setImageUrl(sameUrl);
        requestModel.setPreviewConfiguration("{\"topLeft\":{\"x\":0,\"y\":0},\"topRight\":{\"x\":100,\"y\":0},\"bottomLeft\":{\"x\":0,\"y\":100},\"bottomRight\":{\"x\":100,\"y\":100}}");
        requestModel.setMediaLocationId(media1.getMediaLocation().getId().toString());

        when(mediaRepository.findById(id)).thenReturn(Optional.of(media1));
        when(mediaRepository.save(any(Media.class))).thenReturn(media1);
        doNothing().when(jwtUtils).validateUserIsEmployeeOfBusiness(any(Jwt.class), anyString());

        // Act
        mediaService.updateMediaById(mockJwt, id.toString(), requestModel);

        // Assert
        verify(uploader, never()).destroy(anyString(), anyMap());
        verify(mediaRepository).save(any(Media.class));
    }

    @Test
    void updateMedia_WhenTypeIsPoster_ShouldSetPosterFieldsAndClearDigitalFields() {
        // Arrange
        UUID id = media1.getId();
        media1.setTypeOfDisplay(TypeOfDisplay.DIGITAL);
        media1.setLoopDuration(20);
        media1.setResolution("1920x1080");
        media1.setImageUrl("https://res.cloudinary.com/demo/image/upload/v1/sample.png");

        MediaRequestModel requestModel = new MediaRequestModel();
        requestModel.setTitle("Poster Update");
        requestModel.setMediaOwnerName("Poster Owner");
        requestModel.setPrice(new BigDecimal("350.00"));
        requestModel.setDailyImpressions(15000);
        requestModel.setTypeOfDisplay(TypeOfDisplay.POSTER);
        requestModel.setWidth(48.0);
        requestModel.setHeight(96.0);
        requestModel.setSchedule(media1.getSchedule());
        requestModel.setImageUrl("https://res.cloudinary.com/demo/image/upload/v1/sample.png");
        requestModel.setPreviewConfiguration(
                "{\"topLeft\":{\"x\":0,\"y\":0},\"topRight\":{\"x\":100,\"y\":0},\"bottomLeft\":{\"x\":0,\"y\":100},\"bottomRight\":{\"x\":100,\"y\":100}}");

        when(mediaRepository.findById(id)).thenReturn(Optional.of(media1));
        when(mediaRepository.save(any(Media.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(jwtUtils).validateUserIsEmployeeOfBusiness(any(Jwt.class), anyString());

        // Act
        mediaService.updateMediaById(mockJwt, id.toString(), requestModel);

        // Assert
        assertEquals(TypeOfDisplay.POSTER, media1.getTypeOfDisplay());
        assertEquals(48.0, media1.getWidth());
        assertEquals(96.0, media1.getHeight());
        assertNull(media1.getLoopDuration());
        assertNull(media1.getResolution());
        verify(mediaRepository).save(media1);
    }

    @Test
    void deleteMedia_WhenCloudinaryFails_ShouldStillDeleteFromRepository() throws Exception {
        // Arrange
        UUID id = media1.getId();
        media1.setImageUrl(".../upload/sample.png");
        when(mediaRepository.findById(id)).thenReturn(Optional.of(media1));

        // Simulate Cloudinary exception
        when(uploader.destroy(anyString(), anyMap())).thenThrow(new RuntimeException("API Down"));

        // Act & Assert
        assertDoesNotThrow(() -> mediaService.deleteMedia(id));
        verify(mediaRepository).delete(media1);
    }

    @Test
    void deleteMedia_WithCroppedUrl_ShouldStillFindCorrectPublicId() throws Exception {
        // Arrange
        UUID id = UUID.randomUUID();
        // Complex URL with crop and version
        String croppedUrl = "https://res.cloudinary.com/demo/image/upload/c_crop,w_200/v1/my_image.jpg";

        Media media = new Media();
        media.setId(id);
        media.setImageUrl(croppedUrl);

        when(mediaRepository.findById(id)).thenReturn(Optional.of(media));

        // Act
        mediaService.deleteMedia(id);

        // Assert
        verify(uploader).destroy(eq("my_image"), anyMap());
    }

    @Test
    void deleteMedia_VideoFile_ShouldPassVideoResourceTypeToCloudinary() throws Exception {
        // Arrange
        UUID id = UUID.randomUUID();
        String videoUrl = "https://res.cloudinary.com/demo/video/upload/v1/my_video.mp4";
        media1.setImageUrl(videoUrl);
        when(mediaRepository.findById(id)).thenReturn(Optional.of(media1));

        // Act
        mediaService.deleteMedia(id);

        // Assert
        ArgumentCaptor<Map<String, Object>> mapCaptor = ArgumentCaptor.forClass(Map.class);        verify(uploader).destroy(eq("my_video"), mapCaptor.capture());
        assertEquals("video", mapCaptor.getValue().get("resource_type"));
    }

    @Test
    void deleteMedia_WithNullImageUrl_ShouldNotCallCloudinary() throws Exception {
        // Arrange
        UUID id = UUID.randomUUID();
        media1.setImageUrl(null);
        when(mediaRepository.findById(id)).thenReturn(Optional.of(media1));

        // Act
        mediaService.deleteMedia(id);

        // Assert
        verify(uploader, never()).destroy(anyString(), anyMap());
        verify(mediaRepository).delete(media1); // Should still delete from DB
    }

    // ==================== Helper Methods ====================
        // ==================== Helper Methods ====================

        private Business createBusiness(Integer id) {
                Business business = new Business();
                business.setId(id);
                business.setBusinessId(new BusinessIdentifier(UUID.randomUUID().toString()));

                return business;
        }

        private MediaLocation createMediaLocation(String name, Double latitude, Double longitude) {
                MediaLocation location = new MediaLocation();
                location.setId(UUID.randomUUID());
                location.setName(name);
                location.setCountry("Canada");
                location.setProvince("ON");
                location.setCity("Toronto");
                location.setStreet("123 Main St");
                location.setPostalCode("M5H 1A1");
                location.setLatitude(latitude);
                location.setLongitude(longitude);
                return location;
        }

        private Media createMedia(String title, String businessId, MediaLocation location,
                        BigDecimal price, Integer dailyImpressions, Status status) {
                Media media = new Media();
                media.setId(UUID.randomUUID());
                media.setTitle(title);
                media.setBusinessId(UUID.fromString(businessId));
                media.setMediaLocation(location);
                media.setPrice(price);
                media.setDailyImpressions(dailyImpressions);
                media.setStatus(status);
                media.setMediaOwnerName("Test Owner");

                // Set other required fields
                ScheduleModel schedule = new ScheduleModel();
                WeeklyScheduleEntry entry = new WeeklyScheduleEntry();
                entry.setDayOfWeek("Monday");
                entry.setActive(true);
                entry.setStartTime("09:00");
                entry.setEndTime("17:00");
                schedule.setWeeklySchedule(List.of(entry));
                media.setSchedule(schedule);

                media.setActiveDays(1);

                return media;
        }

        @Test
        void patchMediaStatusById_WhenRejectedReactivated_ShouldThrowMediaRejectedReactivationException() {
                // Arrange
                media1.setStatus(Status.REJECTED);
                when(mediaRepository.findById(media1.getId())).thenReturn(Optional.of(media1));
                MediaStatusPatchRequestModel request = new MediaStatusPatchRequestModel();
                request.setStatus(Status.ACTIVE);

                // Act & Assert
                MediaRejectedReactivationException ex = assertThrows(MediaRejectedReactivationException.class,
                        () -> mediaService.patchMediaStatusById(mockJwt, media1.getId().toString(), request));
                assertEquals("Rejected media cannot be re-activated. Please create a new media item instead.", ex.getMessage());
        }

        @Test
        void patchMediaStatusById_WhenTransitionNotAllowed_ShouldThrowInvalidMediaStatusTransitionException() {
                // Arrange
                media1.setStatus(Status.ACTIVE);
                when(mediaRepository.findById(media1.getId())).thenReturn(Optional.of(media1));
                MediaStatusPatchRequestModel request = new MediaStatusPatchRequestModel();
                request.setStatus(Status.PENDING);

                // Act & Assert
                InvalidMediaStatusTransitionException ex = assertThrows(InvalidMediaStatusTransitionException.class,
                        () -> mediaService.patchMediaStatusById(mockJwt, media1.getId().toString(), request));
                assertEquals("Invalid status transition: ACTIVE -> PENDING", ex.getMessage());
        }

}
