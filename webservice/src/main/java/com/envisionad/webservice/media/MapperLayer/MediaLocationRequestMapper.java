package com.envisionad.webservice.media.MapperLayer;

import com.envisionad.webservice.media.DataAccessLayer.MediaLocation;
import com.envisionad.webservice.media.PresentationLayer.Models.MediaLocationRequestModel;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MediaLocationRequestMapper {

    public MediaLocation requestModelToEntity(MediaLocationRequestModel requestModel) {
        MediaLocation mediaLocation = new MediaLocation();
        mediaLocation.setName(requestModel.getName());
        mediaLocation.setCountry(requestModel.getCountry());
        mediaLocation.setProvince(requestModel.getProvince());
        mediaLocation.setCity(requestModel.getCity());
        mediaLocation.setRegion(requestModel.getRegion());
        mediaLocation.setStreet(requestModel.getStreet());
        mediaLocation.setPostalCode(requestModel.getPostalCode());
        mediaLocation.setLatitude(requestModel.getLatitude());
        mediaLocation.setLongitude(requestModel.getLongitude());
        if (requestModel.getBusinessId() != null) {
            mediaLocation.setBusinessId(UUID.fromString(requestModel.getBusinessId()));
        }
        return mediaLocation;
    }
}
