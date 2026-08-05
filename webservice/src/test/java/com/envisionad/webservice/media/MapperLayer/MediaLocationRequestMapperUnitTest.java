package com.envisionad.webservice.media.MapperLayer;

import com.envisionad.webservice.media.DataAccessLayer.MediaLocation;
import com.envisionad.webservice.media.PresentationLayer.Models.MediaLocationRequestModel;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MediaLocationRequestMapperUnitTest {

    private final MediaLocationRequestMapper mapper = new MediaLocationRequestMapper();

    @Test
    void requestModelToEntity_ShouldMapAllFields() {
        MediaLocationRequestModel requestModel = new MediaLocationRequestModel();
        requestModel.setName("New Location Name");
        requestModel.setStreet("123 Test St");
        requestModel.setCity("Test City");
        requestModel.setRegion("Test Region");
        requestModel.setProvince("Test Province");
        requestModel.setCountry("Test Country");
        requestModel.setPostalCode("12345");
        requestModel.setLatitude(45.5017);
        requestModel.setLongitude(-73.5673);
        requestModel.setBusinessId(UUID.randomUUID().toString());

        MediaLocation entity = mapper.requestModelToEntity(requestModel);

        assertEquals("New Location Name", entity.getName());
        assertEquals("123 Test St", entity.getStreet());
        assertEquals("Test City", entity.getCity());
        assertEquals("Test Region", entity.getRegion());
        assertEquals("Test Province", entity.getProvince());
        assertEquals("Test Country", entity.getCountry());
        assertEquals("12345", entity.getPostalCode());
        assertEquals(45.5017, entity.getLatitude());
        assertEquals(-73.5673, entity.getLongitude());
        assertEquals(UUID.fromString(requestModel.getBusinessId()), entity.getBusinessId());
    }
}
