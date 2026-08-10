package com.envisionad.webservice.media.BusinessLayer;

import com.cloudinary.Cloudinary;
import com.envisionad.webservice.business.dataaccesslayer.BusinessRepository;
import com.envisionad.webservice.business.exceptions.BusinessNotFoundException;
import com.envisionad.webservice.media.DataAccessLayer.Media;
import com.envisionad.webservice.media.DataAccessLayer.Status;
import com.envisionad.webservice.media.DataAccessLayer.MediaRepository;
import com.envisionad.webservice.media.DataAccessLayer.MediaSpecifications;
import com.envisionad.webservice.media.DataAccessLayer.TypeOfDisplay;
import com.envisionad.webservice.media.MapperLayer.MediaResponseMapper;
import com.envisionad.webservice.media.PresentationLayer.Models.MediaRequestModel;
import com.envisionad.webservice.media.PresentationLayer.Models.MediaResponseModel;
import com.envisionad.webservice.media.PresentationLayer.Models.MediaStatusPatchRequestModel;
import com.envisionad.webservice.media.exceptions.MediaNotFoundException;
import com.envisionad.webservice.media.exceptions.MediaRejectedReactivationException;
import com.envisionad.webservice.media.exceptions.InvalidMediaStatusTransitionException;
import com.envisionad.webservice.payment.dataaccesslayer.StripeAccount;
import com.envisionad.webservice.payment.dataaccesslayer.StripeAccountRepository;
import com.envisionad.webservice.payment.exceptions.StripeAccountNotOnboardedException;
import com.envisionad.webservice.utils.CloudinaryConfig;
import com.envisionad.webservice.utils.JwtUtils;
import com.envisionad.webservice.utils.MathFunctions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;

import java.util.*;

@Service
@Slf4j
public class MediaServiceImpl implements MediaService {

    private final MediaRepository mediaRepository;
    private final StripeAccountRepository stripeAccountRepository;
    private final MediaResponseMapper mediaResponseMapper;
    private final JwtUtils jwtUtils;
    private final Cloudinary cloudinary;
    private final BusinessRepository businessRepository;

    public MediaServiceImpl(MediaRepository mediaRepository, StripeAccountRepository stripeAccountRepository, Cloudinary cloudinary, MediaResponseMapper mediaResponseMapper, BusinessRepository businessRepository, JwtUtils jwtUtils) {
        this.mediaRepository = mediaRepository;
        this.stripeAccountRepository = stripeAccountRepository;
        this.cloudinary = cloudinary;
        this.mediaResponseMapper = mediaResponseMapper;
        this.businessRepository = businessRepository;
        this.jwtUtils = jwtUtils;
    }

    @Override
    public List<Media> getAllMedia() {
        return mediaRepository.findAll();
    }

    @Override
    public List<Media> getAllMediaByBusinessId(UUID businessId) {
        return mediaRepository.findMediaByBusinessId(businessId);
    }

    @Override
    public Page<Media> getAllFilteredActiveMedia(
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
            List<String> venueIds
    ) {

        // FILTERING
        Specification<Media> spec = MediaSpecifications.hasStatus(Status.ACTIVE);

        if (title != null) {
            spec = spec.and(MediaSpecifications.titleContains(title));
        }

        if (businessId != null) {
            spec = spec.and(MediaSpecifications.businessIdEquals(UUID.fromString(businessId)));
        }

        if (minPrice != null || maxPrice != null) {
            spec = spec.and(MediaSpecifications.priceBetween(minPrice, maxPrice));
        }

        if (minWeeklyImpressions != null) {
            spec = spec.and(MediaSpecifications.weeklyImpressionsGreaterThan(minWeeklyImpressions));
        }

        if (excludedId != null) {
            spec = spec.and(MediaSpecifications.mediaIdIsNotEqual(UUID.fromString(excludedId)));
        }

        if (bounds != null) {
            spec = spec.and(MediaSpecifications.withinBounds(bounds));
        }

        if (venueIds != null && !venueIds.isEmpty()) {
            spec = spec.and(MediaSpecifications.venueIdIn(venueIds));
        }

        // Sort by Nearest
        if ("nearest".equals(specialSort) && userLat != null && userLng != null) {
            List<Media> list = mediaRepository.findAll(spec);

            list.sort(Comparator.comparingDouble(
                    m -> {
                        if (m.getMediaLocation() == null) {
                            return Double.POSITIVE_INFINITY;
                        }
                        return MathFunctions.distance(
                                userLat,
                                userLng,
                                m.getMediaLocation().getLatitude(),
                                m.getMediaLocation().getLongitude());
                    }));

            int pageStart = (int) pageable.getOffset();
            int pageEnd = Math.min(pageStart + pageable.getPageSize(), list.size());

            if (pageStart >= list.size()) {
                return Page.empty(pageable);
            }
            List<Media> paged = list.subList(pageStart, pageEnd);

            return new PageImpl<>(paged, pageable, list.size());
        }

        if ("weeklyImpressions,asc".equals(specialSort)) {
            List<Media> list = mediaRepository.findAll(spec);

            list.sort(Comparator.comparingInt(
                    m -> (m.getDailyImpressions() == null ? 0 : m.getDailyImpressions())
                            * (m.getActiveDays() == null ? 0 : m.getActiveDays())
            ));

            return paginate(list, pageable);
        }

        if ("weeklyImpressions,desc".equals(specialSort)) {
            List<Media> list = mediaRepository.findAll(spec);

            list.sort(Comparator.comparingInt(
                    (Media m) -> (m.getDailyImpressions() == null ? 0 : m.getDailyImpressions())
                            * (m.getActiveDays() == null ? 0 : m.getActiveDays())
            ).reversed());

            return paginate(list, pageable);
        }

        return mediaRepository.findAll(spec, pageable);
    }

    @Override
    public Media getMediaById(UUID id) {
        return mediaRepository.findById(id).orElse(null);
    }

    @Override
    public List<MediaResponseModel> getMediaByBusinessId(Jwt jwt, String businessId) {
        UUID businessUuid;
        try {
            businessUuid = UUID.fromString(businessId);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid businessId format: " + businessId, ex);
        }

        boolean businessExists = businessRepository.existsByBusinessId_BusinessId(businessId);
        if (!businessExists) {
            throw new BusinessNotFoundException(businessId);
        }

        jwtUtils.validateUserIsEmployeeOfBusiness(jwt, businessId);

        List<Media> mediaList = mediaRepository.findMediaByBusinessId(businessUuid);

        return mediaList.stream().map(mediaResponseMapper::entityToResponseModel).toList();
    }

    @Override
    public Media addMedia(Media media) {
        // Check if the business has a fully onboarded Stripe account
        UUID businessId = media.getBusinessId();
        if (businessId == null) {
            throw new IllegalArgumentException("Business ID is required to create media.");
        }

        // New media always enters moderation flow first.
        media.setStatus(Status.PENDING);

        Optional<StripeAccount> stripeAccountOpt = stripeAccountRepository.findByBusinessId(businessId.toString());

        if (stripeAccountOpt.isEmpty() || !stripeAccountOpt.get().isOnboardingComplete()
                || !stripeAccountOpt.get().isChargesEnabled() || !stripeAccountOpt.get().isPayoutsEnabled()) {
            throw new StripeAccountNotOnboardedException(
                    "Your business must have a fully configured Stripe account to create media. Please complete your Stripe onboarding.");
        }

        return mediaRepository.save(media);
    }

    @Override
    public MediaResponseModel updateMediaById(Jwt jwt, String id, MediaRequestModel requestModel) {
        Media existingMedia = mediaRepository.findById(UUID.fromString(id)).orElseThrow(() -> new MediaNotFoundException(id));

        UUID businessId = existingMedia.getBusinessId();
        if (businessId == null) {
            throw new IllegalStateException("Existing media has no associated business; cannot update.");
        }
        jwtUtils.validateUserIsEmployeeOfBusiness(jwt, businessId.toString());

        // Validate the request model
        MediaRequestValidator.validateMediaUpdateRequest(requestModel);

        // Handle Cloudinary cleanup if image URL is changing
        String oldUrl = existingMedia.getImageUrl();
        String newUrl = requestModel.getImageUrl();

        String normalizedOldUrl = (oldUrl != null && !oldUrl.trim().isEmpty()) ? oldUrl.trim() : null;
        String normalizedNewUrl = (newUrl != null && !newUrl.trim().isEmpty()) ? newUrl.trim() : null;

        if (normalizedOldUrl != null && !Objects.equals(normalizedNewUrl, normalizedOldUrl)) {
            String oldPublicId = CloudinaryConfig.getPublicIdFromUrl(normalizedOldUrl);
            String oldResourceType = CloudinaryConfig.getResourceTypeFromUrl(normalizedOldUrl);

            if (oldPublicId != null) {
                try {
                    log.info("Update: Deleting old {} asset: {}", oldResourceType, oldPublicId);
                    cloudinary.uploader().destroy(oldPublicId, Map.of(
                            "invalidate", true,
                            "resource_type", oldResourceType));
                } catch (Exception e) {
                    log.error("Failed to clean up old {} asset: {}", oldResourceType, e.getMessage());
                }
            }
        }

        // Update fields
        existingMedia.setTitle(requestModel.getTitle());
        existingMedia.setMediaOwnerName(requestModel.getMediaOwnerName());
        existingMedia.setPrice(requestModel.getPrice());
        existingMedia.setDailyImpressions(requestModel.getDailyImpressions());
        existingMedia.setTypeOfDisplay(requestModel.getTypeOfDisplay());

        // Update display-specific fields based on type
        if (requestModel.getTypeOfDisplay() == TypeOfDisplay.DIGITAL) {
            existingMedia.setLoopDuration(requestModel.getLoopDuration());
            existingMedia.setResolution(requestModel.getResolution());
            // Clear poster fields
            existingMedia.setWidth(null);
            existingMedia.setHeight(null);
        } else if (requestModel.getTypeOfDisplay() == TypeOfDisplay.POSTER) {
            existingMedia.setWidth(requestModel.getWidth());
            existingMedia.setHeight(requestModel.getHeight());
            // Clear digital fields
            existingMedia.setLoopDuration(null);
            existingMedia.setResolution(null);
        }

        existingMedia.setSchedule(requestModel.getSchedule());
        existingMedia.setImageUrl(normalizedNewUrl);
        existingMedia.setPreviewConfiguration(requestModel.getPreviewConfiguration());
        existingMedia.setVenueId(requestModel.getVenueId());

        // Set status to pending after update
        existingMedia.setStatus(Status.PENDING);

        Media updatedMedia = mediaRepository.save(existingMedia);
        return mediaResponseMapper.entityToResponseModel(updatedMedia);
    }

    @Override
    public void deleteMedia(Jwt jwt, UUID id) {
        Media media = mediaRepository.findById(id)
                .orElseThrow(() -> new MediaNotFoundException(id.toString()));

        UUID businessId = media.getBusinessId();
        if (businessId == null) {
            throw new IllegalStateException("Existing media has no associated business; cannot delete.");
        }
        jwtUtils.validateUserIsEmployeeOfBusiness(jwt, businessId.toString());

        String publicId = CloudinaryConfig.getPublicIdFromUrl(media.getImageUrl());
        String resourceType = CloudinaryConfig.getResourceTypeFromUrl(media.getImageUrl());

        if (publicId != null) {
            try {
                cloudinary.uploader().destroy(publicId, Map.of(
                        "invalidate", true,
                        "resource_type", resourceType
                ));
                log.info("Successfully deleted {} asset: {}", resourceType, publicId);
            } catch (Exception e) {
                log.error("Cloudinary cleanup failed for {} {}: {}", resourceType, publicId, e.getMessage());
            }
        }
        mediaRepository.delete(media);
    }

    private Page<Media> paginate(List<Media> list, Pageable pageable) {
        int pageStart = (int) pageable.getOffset();
        int pageEnd = Math.min(pageStart + pageable.getPageSize(), list.size());

        if (pageStart >= list.size()) {
            return Page.empty(pageable);
        }

        return new PageImpl<>(list.subList(pageStart, pageEnd), pageable, list.size());
    }

    @Override
    public MediaResponseModel patchMediaStatusById(Jwt jwt, String id, MediaStatusPatchRequestModel request) {

        Media media = mediaRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new MediaNotFoundException(id));

        Status current = media.getStatus();
        Status target = request.getStatus();

        if (current == target) return mediaResponseMapper.entityToResponseModel(media);
        if (current == Status.REJECTED) throw new MediaRejectedReactivationException();

        boolean allowed =
                (current == Status.PENDING && (target == Status.ACTIVE || target == Status.REJECTED))
                        || (current == Status.ACTIVE && target == Status.INACTIVE)
                        || (current == Status.INACTIVE && target == Status.ACTIVE);

        if (!allowed) throw new InvalidMediaStatusTransitionException(current, target);

        // Authorization rules
        // - Admin can moderate (PENDING -> ACTIVE/REJECTED)
        // - Business employees can (ACTIVE<->INACTIVE)
        boolean isModerationAction = current == Status.PENDING;

        boolean isAdmin = jwtUtils.hasAuthority("patch:media_status");

        if (isModerationAction) {
            // Only admins can approve/reject pending media
            if (!isAdmin) {
                throw new AccessDeniedException("Only admins can approve/reject pending media.");
            }
        } else {
            // Only the owning business can toggle ACTIVE<->INACTIVE
            UUID businessId = media.getBusinessId();
            if (businessId == null) {
                throw new IllegalStateException("Media has no associated business.");
            }
            jwtUtils.validateUserIsEmployeeOfBusiness(jwt, businessId.toString());
        }


        media.setStatus(target);
        Media saved = mediaRepository.save(media);
        return mediaResponseMapper.entityToResponseModel(saved);
    }


    @Override
    public List<Media> getMediaByStatus(Status status) {
        return mediaRepository.findByStatus(status);
    }


}
