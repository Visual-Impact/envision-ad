package com.envisionad.webservice.media.PresentationLayer;

import com.envisionad.webservice.media.BusinessLayer.MediaService;
import com.envisionad.webservice.media.DataAccessLayer.Media;
import com.envisionad.webservice.media.DataAccessLayer.Status;
import com.envisionad.webservice.media.MapperLayer.MediaRequestMapper;
import com.envisionad.webservice.media.MapperLayer.MediaResponseMapper;
import com.envisionad.webservice.media.PresentationLayer.Models.MediaRequestModel;
import com.envisionad.webservice.media.PresentationLayer.Models.MediaResponseModel;
import com.envisionad.webservice.media.PresentationLayer.Models.MediaStatusPatchRequestModel;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.envisionad.webservice.media.BusinessLayer.MediaRequestValidator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;

import java.util.List;
import java.util.UUID;

import com.envisionad.webservice.business.businesslogiclayer.BusinessService;
import com.envisionad.webservice.business.presentationlayer.models.BusinessResponseModel;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/media")
@Slf4j
@CrossOrigin(origins = { "http://localhost:3000", "https://envision-ad.ca" })
public class MediaController {

    private final MediaService mediaService;
    private final MediaRequestMapper requestMapper;
    private final MediaResponseMapper responseMapper;
    private final BusinessService businessService;

    public MediaController(MediaService mediaService,
            MediaRequestMapper requestMapper,
            MediaResponseMapper responseMapper,
            BusinessService businessService) {
        this.mediaService = mediaService;
        this.requestMapper = requestMapper;
        this.responseMapper = responseMapper;
        this.businessService = businessService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('get:media')")
    public List<MediaResponseModel> getAllMedia() {
        return responseMapper.entityListToResponseModelList(mediaService.getAllMedia());
    }

    @GetMapping("/active")
    public ResponseEntity<?> getAllFilteredActiveMedia(
            Pageable pageable,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String businessId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Integer minWeeklyImpressions,
            @RequestParam(required = false) String specialSort,
            @RequestParam(required = false) Double userLat,
            @RequestParam(required = false) Double userLng,
            @RequestParam(required = false) List<Double> bounds,
            @RequestParam(required = false) String excludedId,
            @RequestParam(required = false) List<String> venueIds
            ) {

        if (minPrice != null && minPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("minPrice must be non-negative.");
        }
        if (maxPrice != null && maxPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("maxPrice must be non-negative.");
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new IllegalArgumentException("minPrice must not be greater than maxPrice.");
        }
        if (minWeeklyImpressions != null && minWeeklyImpressions < 0) {
            throw new IllegalArgumentException("minWeeklyImpressions must be non-negative.");
        }

        if (bounds != null && bounds.size() != 4) {
            throw new IllegalArgumentException("bounds must have a length of exactly 4.");
        }

        Page<MediaResponseModel> responsePage = mediaService.getAllFilteredActiveMedia(
                pageable,
                title,
                businessId,
                minPrice,
                maxPrice,
                minWeeklyImpressions,
                specialSort,
                userLat,
                userLng,
                bounds,
                excludedId,
                venueIds).map(responseMapper::entityToResponseModel);

        return ResponseEntity.ok(responsePage);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MediaResponseModel> getMediaById(@PathVariable String id) {
        Media media = mediaService.getMediaById(UUID.fromString(id));
        if (media == null) {
            return ResponseEntity.notFound().build(); // Returns 404 if not found
        }
        return ResponseEntity.ok(responseMapper.entityToResponseModel(media));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('create:media')")
    public ResponseEntity<MediaResponseModel> addMedia(@AuthenticationPrincipal Jwt jwt,
            @RequestBody MediaRequestModel requestModel) {
        MediaRequestValidator.validateMediaRequest(requestModel);

        if (jwt != null) {
            try {
                BusinessResponseModel business = businessService.getBusinessByUserId(jwt, jwt.getSubject());
                if (business != null && business.getBusinessId() != null) {
                    requestModel.setBusinessId(business.getBusinessId());
                }
            } catch (Exception e) {
                log.error("Error fetching business for user {}: {}", jwt.getSubject(), e.getMessage(), e);
            }
        }

        Media entity = requestMapper.requestModelToEntity(requestModel);
        Media savedEntity = mediaService.addMedia(entity);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(responseMapper.entityToResponseModel(savedEntity));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('update:media')")
    public ResponseEntity<MediaResponseModel> updateMedia(@AuthenticationPrincipal Jwt jwt, @PathVariable String id, @RequestBody MediaRequestModel requestModel) {
        return ResponseEntity.ok(mediaService.updateMediaById(jwt, id, requestModel));
    }

    // TODO: likely to become an archive/soft-delete instead of a hard delete
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('update:media')")
    public ResponseEntity<Void> deleteMedia(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        mediaService.deleteMedia(jwt, UUID.fromString(id));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('update:media') || hasAuthority('patch:media_status')")
    public ResponseEntity<MediaResponseModel> patchMediaStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id,
            @Valid @RequestBody MediaStatusPatchRequestModel request
    ) {
        return ResponseEntity.ok(mediaService.patchMediaStatusById(jwt, id, request));
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('get:media')")
    public List<MediaResponseModel> getPendingMedia() {
        return responseMapper.entityListToResponseModelList(
                mediaService.getMediaByStatus(Status.PENDING)
        );
    }


}