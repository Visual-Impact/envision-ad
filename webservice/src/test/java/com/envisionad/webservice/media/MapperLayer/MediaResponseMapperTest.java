package com.envisionad.webservice.media.MapperLayer;

import com.envisionad.webservice.business.dataaccesslayer.Business;
import com.envisionad.webservice.business.dataaccesslayer.BusinessRepository;
import com.envisionad.webservice.media.DataAccessLayer.Media;
import com.envisionad.webservice.media.DataAccessLayer.MediaLocation;
import com.envisionad.webservice.media.PresentationLayer.Models.MediaResponseModel;
import com.envisionad.webservice.venue.dataaccesslayer.VenueRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MediaResponseMapperTest {

    private MediaResponseMapper mapper;
    private BusinessRepository businessRepository;
    private VenueRepository venueRepository;

    @BeforeEach
    void setUp() {
        businessRepository = Mockito.mock(BusinessRepository.class);
        venueRepository = Mockito.mock(VenueRepository.class);
        mapper = new MediaResponseMapper(businessRepository, venueRepository);
    }

    @Test
    void entityToResponseModel_WithBusinessId_ShouldMapBusinessId() {
        UUID businessId = UUID.randomUUID();

        Media media = new Media();
        media.setId(UUID.randomUUID());
        media.setBusinessId(businessId);

        MediaResponseModel response = mapper.entityToResponseModel(media);

        assertNotNull(response);
        assertEquals(businessId.toString(), response.getBusinessId());
    }

    @Test
    void entityToResponseModel_WithImageUrl_ShouldMapImageUrl() {
        String imageUrl = "http://example.com/image.jpg";

        Media media = new Media();
        media.setImageUrl(imageUrl);
        media.setId(UUID.randomUUID());

        MediaResponseModel response = mapper.entityToResponseModel(media);

        assertNotNull(response);
        assertEquals(imageUrl, response.getImageUrl());
    }

    @Test
    void entityToResponseModel_WithMediaLocation_ShouldMapLocation() {
        UUID locationId = UUID.randomUUID();

        MediaLocation location = new MediaLocation();
        location.setId(locationId);
        location.setName("Test Location");

        Media media = new Media();
        media.setId(UUID.randomUUID());
        media.setMediaLocation(location);

        MediaResponseModel response = mapper.entityToResponseModel(media);

        assertNotNull(response);
        assertNotNull(response.getMediaLocation());
        assertEquals(locationId, response.getMediaLocation().getId());
        assertEquals("Test Location", response.getMediaLocation().getName());
    }

    @Test
    void entityToResponseModel_WithMediaLocation_ShouldMapRegionAndBusinessId() {
        UUID locationBusinessId = UUID.randomUUID();

        MediaLocation location = new MediaLocation();
        location.setId(UUID.randomUUID());
        location.setName("Test Location");
        location.setRegion("Montérégie");
        location.setBusinessId(locationBusinessId);

        Media media = new Media();
        media.setId(UUID.randomUUID());
        media.setMediaLocation(location);

        MediaResponseModel response = mapper.entityToResponseModel(media);

        assertNotNull(response.getMediaLocation());
        assertEquals("Montérégie", response.getMediaLocation().getRegion());
        assertEquals(locationBusinessId, response.getMediaLocation().getBusinessId());
    }

    @Test
    void entityToResponseModel_WithMediaLocationWithoutRegion_ShouldMapNullRegion() {
        MediaLocation location = new MediaLocation();
        location.setId(UUID.randomUUID());
        location.setName("Test Location");

        Media media = new Media();
        media.setId(UUID.randomUUID());
        media.setMediaLocation(location);

        MediaResponseModel response = mapper.entityToResponseModel(media);

        assertNotNull(response.getMediaLocation());
        assertNull(response.getMediaLocation().getRegion());
    }

    @Test
    void entityToResponseModel_WithPreviewConfiguration_ShouldMapPreviewConfiguration() {
        String previewConfig = "{\"corners\": []}";

        Media media = new Media();
        media.setPreviewConfiguration(previewConfig);

        MediaResponseModel response = mapper.entityToResponseModel(media);

        assertNotNull(response);
        assertEquals(previewConfig, response.getPreviewConfiguration());
    }

    @Test
    void entityToResponseModel_WithNullPreviewConfiguration_ShouldMapNull() {
        Media media = new Media();
        media.setPreviewConfiguration(null);

        MediaResponseModel response = mapper.entityToResponseModel(media);

        assertNotNull(response);
        assertNull(response.getPreviewConfiguration());
    }

    @Test
    void entityToResponseModel_WithBusinessId_ShouldMapBusinessName() {
        UUID businessId = UUID.randomUUID();

        Media media = new Media();
        media.setId(UUID.randomUUID());
        media.setBusinessId(businessId);

        Business business = new Business();
        business.setName("Test Business");

        Mockito.when(
                businessRepository.findByBusinessId_BusinessId(businessId.toString())
        ).thenReturn(business);

        MediaResponseModel response = mapper.entityToResponseModel(media);

        assertNotNull(response);
        assertEquals("Test Business", response.getBusinessName());
    }

    @Test
    void entityToResponseModel_WithBusinessId_BusinessNotFound_ShouldSetEmptyBusinessName() {
        UUID businessId = UUID.randomUUID();

        Media media = new Media();
        media.setId(UUID.randomUUID());
        media.setBusinessId(businessId);

        Mockito.when(
                businessRepository.findByBusinessId_BusinessId(businessId.toString())
        ).thenReturn(null);

        MediaResponseModel response = mapper.entityToResponseModel(media);

        assertNotNull(response);
        assertEquals("", response.getBusinessName());
    }

}
