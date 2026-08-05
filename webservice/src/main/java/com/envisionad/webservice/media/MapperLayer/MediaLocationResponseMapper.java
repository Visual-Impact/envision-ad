package com.envisionad.webservice.media.MapperLayer;

import com.envisionad.webservice.media.DataAccessLayer.MediaLocation;
import com.envisionad.webservice.media.PresentationLayer.Models.MediaLocationResponseModel;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class MediaLocationResponseMapper {

    private final MediaResponseMapper mediaResponseMapper;

    public MediaLocationResponseMapper(MediaResponseMapper mediaResponseMapper) {
        this.mediaResponseMapper = mediaResponseMapper;
    }

    public MediaLocationResponseModel entityToResponseModel(MediaLocation entity) {
        MediaLocationResponseModel model = new MediaLocationResponseModel();
        model.setId(entity.getId());
        model.setName(entity.getName());
        model.setCountry(entity.getCountry());
        model.setProvince(entity.getProvince());
        model.setCity(entity.getCity());
        model.setRegion(entity.getRegion());
        model.setStreet(entity.getStreet());
        model.setPostalCode(entity.getPostalCode());
        model.setLatitude(entity.getLatitude());
        model.setLongitude(entity.getLongitude());
        model.setBusinessId(entity.getBusinessId());

        if (entity.getMediaList() != null && Hibernate.isInitialized(entity.getMediaList())) {
            model.setMediaList(mediaResponseMapper.entityListToResponseModelList(entity.getMediaList()));
        }

        return model;
    }

    public List<MediaLocationResponseModel> entityListToResponseModelList(List<MediaLocation> entities) {
        return entities.stream()
                .map(this::entityToResponseModel)
                .collect(Collectors.toList());
    }
}
