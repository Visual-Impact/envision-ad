package com.envisionad.webservice.media.MapperLayer;

import com.envisionad.webservice.media.DataAccessLayer.Media;
import com.envisionad.webservice.media.DataAccessLayer.MediaLocation;
import com.envisionad.webservice.media.PresentationLayer.Models.MediaLocationResponseModel;
import com.envisionad.webservice.media.PresentationLayer.Models.MediaResponseModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaLocationResponseMapperTest {

    @Mock
    private MediaResponseMapper mediaResponseMapper;

    @InjectMocks
    private MediaLocationResponseMapper mapper;

    @Test
    void entityToResponseModel_WithMediaList_ShouldMapAllFieldsAndMediaList() {
        UUID id = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();

        Media media = new Media();
        media.setId(UUID.randomUUID());

        MediaLocation entity = new MediaLocation();
        entity.setId(id);
        entity.setName("Downtown");
        entity.setCountry("Canada");
        entity.setProvince("QC");
        entity.setCity("Montreal");
        entity.setRegion("Montérégie");
        entity.setStreet("123 Main St");
        entity.setPostalCode("H1H 1H1");
        entity.setLatitude(45.5017);
        entity.setLongitude(-73.5673);
        entity.setBusinessId(businessId);
        entity.setMediaList(List.of(media));

        MediaResponseModel mediaResponseModel = new MediaResponseModel();
        mediaResponseModel.setId(media.getId());
        List<MediaResponseModel> mappedMedia = List.of(mediaResponseModel);
        when(mediaResponseMapper.entityListToResponseModelList(entity.getMediaList())).thenReturn(mappedMedia);

        MediaLocationResponseModel result = mapper.entityToResponseModel(entity);

        assertNotNull(result);
        assertEquals(id, result.getId());
        assertEquals("Downtown", result.getName());
        assertEquals("Canada", result.getCountry());
        assertEquals("QC", result.getProvince());
        assertEquals("Montreal", result.getCity());
        assertEquals("Montérégie", result.getRegion());
        assertEquals("123 Main St", result.getStreet());
        assertEquals("H1H 1H1", result.getPostalCode());
        assertEquals(45.5017, result.getLatitude());
        assertEquals(-73.5673, result.getLongitude());
        assertEquals(businessId, result.getBusinessId());
        assertEquals(mappedMedia, result.getMediaList());
        verify(mediaResponseMapper).entityListToResponseModelList(entity.getMediaList());
    }

    @Test
    void entityToResponseModel_WithNullMediaList_ShouldNotMapMediaList() {
        MediaLocation entity = new MediaLocation();
        entity.setId(UUID.randomUUID());
        entity.setName("No Media");
        entity.setCountry("Canada");
        entity.setProvince("ON");
        entity.setCity("Toronto");
        entity.setStreet("456 Queen St");
        entity.setPostalCode("M5H 2N2");
        entity.setLatitude(43.6532);
        entity.setLongitude(-79.3832);
        entity.setBusinessId(UUID.randomUUID());
        entity.setMediaList(null);

        MediaLocationResponseModel result = mapper.entityToResponseModel(entity);

        assertNotNull(result);
        assertNull(result.getMediaList());
        verifyNoInteractions(mediaResponseMapper);
    }

    @Test
    void entityListToResponseModelList_ShouldMapEachEntity() {
        MediaLocation entity1 = new MediaLocation();
        entity1.setId(UUID.randomUUID());
        entity1.setName("Location 1");
        entity1.setCountry("Canada");
        entity1.setProvince("QC");
        entity1.setCity("Montreal");
        entity1.setStreet("123 Main St");
        entity1.setPostalCode("H1H 1H1");
        entity1.setLatitude(45.5017);
        entity1.setLongitude(-73.5673);
        entity1.setBusinessId(UUID.randomUUID());
        entity1.setMediaList(null);

        MediaLocation entity2 = new MediaLocation();
        entity2.setId(UUID.randomUUID());
        entity2.setName("Location 2");
        entity2.setCountry("Canada");
        entity2.setProvince("ON");
        entity2.setCity("Toronto");
        entity2.setStreet("456 Queen St");
        entity2.setPostalCode("M5H 2N2");
        entity2.setLatitude(43.6532);
        entity2.setLongitude(-79.3832);
        entity2.setBusinessId(UUID.randomUUID());
        entity2.setMediaList(null);

        List<MediaLocationResponseModel> result = mapper.entityListToResponseModelList(List.of(entity1, entity2));

        assertEquals(2, result.size());
        assertEquals(entity1.getId(), result.get(0).getId());
        assertEquals(entity2.getId(), result.get(1).getId());
        assertEquals("Location 1", result.get(0).getName());
        assertEquals("Location 2", result.get(1).getName());
    }
}
