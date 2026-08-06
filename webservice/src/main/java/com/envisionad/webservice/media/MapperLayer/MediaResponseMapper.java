package com.envisionad.webservice.media.MapperLayer;

import com.envisionad.webservice.business.dataaccesslayer.BusinessRepository;
import com.envisionad.webservice.media.DataAccessLayer.Media;
import com.envisionad.webservice.media.PresentationLayer.Models.MediaLocationResponseModel;
import com.envisionad.webservice.media.PresentationLayer.Models.MediaResponseModel;
import com.envisionad.webservice.venue.dataaccesslayer.Venue;
import com.envisionad.webservice.venue.dataaccesslayer.VenueRepository;
import com.envisionad.webservice.venue.presentationlayer.models.VenueResponseModel;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MediaResponseMapper {
    private final BusinessRepository businessRepository;
    private final VenueRepository venueRepository;

    public MediaResponseMapper(BusinessRepository businessRepository, VenueRepository venueRepository) {
        this.businessRepository = businessRepository;
        this.venueRepository = venueRepository;
    }

    public MediaResponseModel entityToResponseModel(Media media) {

        MediaResponseModel response = new MediaResponseModel();

        response.setId(media.getId());
        response.setTitle(media.getTitle());
        response.setMediaOwnerName(media.getMediaOwnerName());
        response.setResolution(media.getResolution());
        response.setLoopDuration(media.getLoopDuration());
        response.setTypeOfDisplay(media.getTypeOfDisplay());
        response.setSchedule(media.getSchedule());
        response.setStatus(media.getStatus());
        response.setWidth(media.getWidth());
        response.setHeight(media.getHeight());
        response.setPrice(media.getPrice());
        response.setDailyImpressions(media.getDailyImpressions());
        response.setPreviewConfiguration(media.getPreviewConfiguration());

        if (media.getBusinessId() != null) {

            response.setBusinessId(media.getBusinessId().toString());

            var business = businessRepository.findByBusinessId_BusinessId(media.getBusinessId().toString());

            if (business != null) {
                response.setBusinessName(business.getName());
            } else {
                response.setBusinessName("");
            }
        }

        if (media.getVenueId() != null) {
            venueRepository.findByVenueId(media.getVenueId()).ifPresent(venue -> {
                VenueResponseModel venueResponse = new VenueResponseModel();
                venueResponse.setVenueId(venue.getVenueId());
                venueResponse.setNameEn(venue.getNameEn());
                venueResponse.setNameFr(venue.getNameFr());
                venueResponse.setColorCode(venue.getColorCode());
                response.setVenue(venueResponse);
            });
        }

        // Using Cloudinary URL
        if (media.getImageUrl() != null && !media.getImageUrl().isBlank()) {
            response.setImageUrl(media.getImageUrl());
        }

        if (media.getMediaLocation() != null) {
            MediaLocationResponseModel mediaLocationResponseModel = getMediaLocationResponseModel(media);

            response.setMediaLocation(mediaLocationResponseModel);
        }

        return response;
    }

    private static MediaLocationResponseModel getMediaLocationResponseModel(Media media) {
        MediaLocationResponseModel mediaLocationResponseModel = new MediaLocationResponseModel();
        mediaLocationResponseModel.setId(media.getMediaLocation().getId());
        mediaLocationResponseModel.setName(media.getMediaLocation().getName());
        mediaLocationResponseModel.setCountry(media.getMediaLocation().getCountry());
        mediaLocationResponseModel.setProvince(media.getMediaLocation().getProvince());
        mediaLocationResponseModel.setCity(media.getMediaLocation().getCity());
        mediaLocationResponseModel.setRegion(media.getMediaLocation().getRegion());
        mediaLocationResponseModel.setStreet(media.getMediaLocation().getStreet());
        mediaLocationResponseModel.setPostalCode(media.getMediaLocation().getPostalCode());
        mediaLocationResponseModel.setLatitude(media.getMediaLocation().getLatitude());
        mediaLocationResponseModel.setLongitude(media.getMediaLocation().getLongitude());
        mediaLocationResponseModel.setBusinessId(media.getMediaLocation().getBusinessId());
        return mediaLocationResponseModel;
    }

    public List<MediaResponseModel> entityListToResponseModelList(List<Media> list) {
        return list.stream()
                .map(this::entityToResponseModel)
                .toList();
    }

}
