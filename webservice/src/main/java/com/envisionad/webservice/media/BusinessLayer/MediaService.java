package com.envisionad.webservice.media.BusinessLayer;

import com.envisionad.webservice.media.DataAccessLayer.Media;
import com.envisionad.webservice.media.DataAccessLayer.Status;
import com.envisionad.webservice.media.PresentationLayer.Models.MediaRequestModel;
import com.envisionad.webservice.media.PresentationLayer.Models.MediaResponseModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;
import com.envisionad.webservice.media.PresentationLayer.Models.MediaStatusPatchRequestModel;

import java.util.List;
import java.math.BigDecimal;
import java.util.UUID;

public interface MediaService {

    List<Media> getAllMedia();

    List<Media> getAllMediaByBusinessId(UUID businessId);

    Page<Media> getAllFilteredActiveMedia(
            Pageable pageable,
            String title,
            String businessId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Integer minWeeklyImpressions,
            String specialSort,
            Double userLat,
            Double userLng,
            List<Double> bounds,
            String excludedId,
            List<String> venueIds);

    Media getMediaById(UUID id);

    List<MediaResponseModel> getMediaByBusinessId(Jwt jwt, String businessId);

    Media addMedia(Media media);

    MediaResponseModel updateMediaById(Jwt jwt, String id, MediaRequestModel requestModel);
    void deleteMedia(Jwt jwt, UUID id);


    MediaResponseModel patchMediaStatusById(Jwt jwt, String id, MediaStatusPatchRequestModel request);

    List<Media> getMediaByStatus(Status status);
}