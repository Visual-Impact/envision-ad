package com.envisionad.webservice.media.BusinessLayer;

import com.envisionad.webservice.media.DataAccessLayer.Media;
import com.envisionad.webservice.media.DataAccessLayer.MediaLocation;
import com.envisionad.webservice.media.DataAccessLayer.MediaLocationRepository;
import com.envisionad.webservice.media.DataAccessLayer.Status;
import com.envisionad.webservice.media.exceptions.MediaLocationDeletionNotAllowedException;
import com.envisionad.webservice.media.exceptions.GeocodingServiceUnavailableException;
import com.envisionad.webservice.media.exceptions.MediaLocationValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.envisionad.webservice.business.businesslogiclayer.BusinessService;
import com.envisionad.webservice.business.presentationlayer.models.BusinessResponseModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaLocationServiceImpl implements MediaLocationService {

    private static final int STREET_MAX_LENGTH = 255;
    private static final int CITY_MAX_LENGTH = 100;
    private static final int PROVINCE_MAX_LENGTH = 100;
    private static final int REGION_MAX_LENGTH = 100;
    private static final int COUNTRY_MAX_LENGTH = 100;
    private static final int POSTAL_CODE_MAX_LENGTH = 20;
    private static final Pattern POSTAL_CODE_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9\\s-]{1,19}$");
    private static final String INVALID_ADDRESS_ERROR = "We couldn't verify this address with OpenStreetMap. "
            + "Please double check street, city, province/state, country, and postal code, or use the map to drop a pin manually.";
    private static final String COORDINATE_EXTRACTION_ERROR = "Address was matched but coordinates could not be determined. Please check street, city, province/state, country, and postal code, or use the map to drop a pin manually.";
    private static final String GEOCODING_UNAVAILABLE_ERROR = "Address validation service is temporarily unavailable. Please try again shortly.";
    private static final String LOCATION_DELETE_BLOCKED_MEDIA_ERROR = "Cannot delete media location while active, pending, or rejected media are assigned. Please delete those media first.";
    private static final Map<String, String> ADDRESS_NOT_FOUND_FIELD_ERRORS = Map.of(
            "street", "Verify the street name or number.",
            "city", "Verify the city value.",
            "province", "Verify the province/state value.",
            "country", "Verify the country value.",
            "postalCode", "Verify the postal code value.");
    private static final double MIN_LATITUDE = -90.0;
    private static final double MAX_LATITUDE = 90.0;
    private static final double MIN_LONGITUDE = -180.0;
    private static final double MAX_LONGITUDE = 180.0;

    private final MediaLocationRepository mediaLocationRepository;
    private final BusinessService businessService;
    private final GeocodingService geocodingService;
    private final ObjectMapper objectMapper;

    @Override
    public List<MediaLocation> getAllMediaLocations(Jwt jwt, String businessId) {
        UUID resolvedBusinessId = resolveBusinessId(jwt).orElse(null);
        if (resolvedBusinessId == null) {
            throw new IllegalArgumentException("Business ID is required");
        }

        UUID targetBusinessId = resolvedBusinessId;
        if (businessId != null) {
            UUID requestedBusinessId = UUID.fromString(businessId);
            if (!requestedBusinessId.equals(resolvedBusinessId)) {
                throw new IllegalArgumentException("Requested business ID does not match the authenticated user's business.");
            }
            targetBusinessId = requestedBusinessId;
        }

        return mediaLocationRepository.findAllByBusinessId(targetBusinessId);
    }

    @Override
    public MediaLocation getMediaLocationById(UUID id) {
        return mediaLocationRepository.findById(id).orElse(null);
    }

    @Override
    public MediaLocation createMediaLocation(MediaLocation mediaLocation, Jwt jwt) {
        UUID resolvedBusinessId = resolveBusinessId(jwt).orElse(null);
        if (resolvedBusinessId == null) {
            throw new IllegalArgumentException("Business ID is required to create a media location.");
        }
        UUID incomingBusinessId = mediaLocation.getBusinessId();
        if (incomingBusinessId != null && !incomingBusinessId.equals(resolvedBusinessId)) {
            throw new IllegalArgumentException("Provided business ID does not match the authenticated user's business.");
        }
        mediaLocation.setBusinessId(resolvedBusinessId);

        validateAndGeocode(mediaLocation);

        return mediaLocationRepository.save(mediaLocation);
    }

    @Override
    public MediaLocation updateMediaLocation(UUID id, MediaLocation mediaLocation) {
        MediaLocation existing = mediaLocationRepository.findById(id).orElse(null);
        if (existing == null) {
            return null;
        }

        mediaLocation.setId(id);
        mediaLocation.setBusinessId(existing.getBusinessId());

        validateAndGeocode(mediaLocation);

        return mediaLocationRepository.save(mediaLocation);
    }

    @Override
    @Transactional
    public void deleteMediaLocation(UUID id) {
        MediaLocation location = mediaLocationRepository.findById(id).orElse(null);
        if (location == null) {
            return;
        }

        List<Media> assignedMedia = location.getMediaList();
        if (assignedMedia != null && !assignedMedia.isEmpty()) {
            boolean hasBlockedMedia = assignedMedia.stream().anyMatch(media ->
                    media.getStatus() == Status.ACTIVE
                            || media.getStatus() == Status.PENDING
                            || media.getStatus() == Status.REJECTED);
            if (hasBlockedMedia) {
                throw new MediaLocationDeletionNotAllowedException(LOCATION_DELETE_BLOCKED_MEDIA_ERROR);
            }
        }

        mediaLocationRepository.delete(location);
    }

    private Optional<UUID> resolveBusinessId(Jwt jwt) {
        if (jwt == null) {
            return Optional.empty();
        }

        try {
            BusinessResponseModel business = businessService.getBusinessByUserId(jwt, jwt.getSubject());
            if (business == null || business.getBusinessId() == null) {
                return Optional.empty();
            }
            return Optional.of(UUID.fromString(business.getBusinessId()));
        } catch (Exception e) {
            log.error("Error fetching business for user {}: {}", jwt.getSubject(), e.getMessage(), e);
            return Optional.empty();
        }
    }

    private void validateAndGeocode(MediaLocation mediaLocation) {
        validateAndNormalizeAddressFields(mediaLocation);

        if (Boolean.TRUE.equals(mediaLocation.getManualCoordinates())) {
            validateManualCoordinates(mediaLocation);
            mediaLocation.setGeocodingResponse(null);
            return;
        }

        String jsonResponse = geocodeAddress(mediaLocation);
        mediaLocation.setGeocodingResponse(jsonResponse);

        try {
            JsonNode firstResult = readFirstResult(jsonResponse)
                    .orElseThrow(() -> new IllegalStateException(COORDINATE_EXTRACTION_ERROR));
            if (!firstResult.has("lat") || !firstResult.has("lon")) {
                throw new IllegalStateException(COORDINATE_EXTRACTION_ERROR);
            }

            mediaLocation.setLatitude(Double.parseDouble(firstResult.get("lat").asText()));
            mediaLocation.setLongitude(Double.parseDouble(firstResult.get("lon").asText()));
        } catch (Exception e) {
            log.error("Error extracting coordinates from geocoding response for address in {}, {}: {}",
                    mediaLocation.getCity(), mediaLocation.getCountry(), e.getMessage(), e);
            throw new MediaLocationValidationException(COORDINATE_EXTRACTION_ERROR, Map.of(), e);
        }
    }

    /**
     * Tries Nominatim's structured query first (one request per field, so it isn't sensitive
     * to how the fields are concatenated), then falls back to a single free-text query. Does
     * not re-validate the returned address against what the user typed — the autocomplete flow
     * on the frontend is what keeps those in sync; this is just resolving coordinates.
     */
    private String geocodeAddress(MediaLocation mediaLocation) {
        try {
            Optional<String> structuredMatch = geocodingService.geocodeStructuredAddress(
                    mediaLocation.getStreet(),
                    mediaLocation.getCity(),
                    mediaLocation.getProvince(),
                    mediaLocation.getCountry(),
                    mediaLocation.getPostalCode());
            if (structuredMatch.isPresent()) {
                return structuredMatch.get();
            }

            String freeTextQuery = String.format("%s, %s, %s, %s, %s",
                    mediaLocation.getStreet(), mediaLocation.getCity(), mediaLocation.getProvince(),
                    mediaLocation.getCountry(), mediaLocation.getPostalCode());
            Optional<String> freeTextMatch = geocodingService.geocodeAddress(freeTextQuery);
            if (freeTextMatch.isPresent()) {
                return freeTextMatch.get();
            }
        } catch (GeocodingServiceUnavailableException e) {
            log.error("Geocoding service unavailable while validating address in {}, {}: {}",
                    mediaLocation.getCity(), mediaLocation.getCountry(), e.getMessage(), e);
            throw new GeocodingServiceUnavailableException(GEOCODING_UNAVAILABLE_ERROR, e);
        }

        log.info("Address verification returned no geocoding match for street='{}', city='{}', province='{}', country='{}', postalCode='{}'",
                mediaLocation.getStreet(), mediaLocation.getCity(), mediaLocation.getProvince(),
                mediaLocation.getCountry(), mediaLocation.getPostalCode());
        throw new MediaLocationValidationException(INVALID_ADDRESS_ERROR, ADDRESS_NOT_FOUND_FIELD_ERRORS);
    }

    private Optional<JsonNode> readFirstResult(String jsonResponse) throws Exception {
        JsonNode rootNode = objectMapper.readTree(jsonResponse);
        if (!rootNode.isArray() || rootNode.size() == 0) {
            return Optional.empty();
        }
        return Optional.of(rootNode.get(0));
    }

    private void validateManualCoordinates(MediaLocation mediaLocation) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        Double latitude = mediaLocation.getLatitude();
        Double longitude = mediaLocation.getLongitude();

        if (latitude == null || latitude < MIN_LATITUDE || latitude > MAX_LATITUDE) {
            fieldErrors.put("latitude", "Latitude must be between -90 and 90.");
        }
        if (longitude == null || longitude < MIN_LONGITUDE || longitude > MAX_LONGITUDE) {
            fieldErrors.put("longitude", "Longitude must be between -180 and 180.");
        }

        if (!fieldErrors.isEmpty()) {
            throw new MediaLocationValidationException(
                    "Please drop a valid pin on the map for this location.", fieldErrors);
        }
    }

    private void validateAndNormalizeAddressFields(MediaLocation mediaLocation) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();

        String street = normalize(mediaLocation.getStreet());
        String city = normalize(mediaLocation.getCity());
        String province = normalize(mediaLocation.getProvince());
        String country = normalize(mediaLocation.getCountry());
        String postalCode = normalize(mediaLocation.getPostalCode());
        // Optional, and deliberately not part of the geocoding candidates below —
        // it exists only for bundle REGION rule matching.
        String region = normalize(mediaLocation.getRegion());

        validateRequiredAndLength(street, "street", "Street is required.", STREET_MAX_LENGTH,
                "Street must be 255 characters or fewer.", fieldErrors);
        validateRequiredAndLength(city, "city", "City is required.", CITY_MAX_LENGTH,
                "City must be 100 characters or fewer.", fieldErrors);
        validateRequiredAndLength(province, "province", "Province/State is required.", PROVINCE_MAX_LENGTH,
                "Province/State must be 100 characters or fewer.", fieldErrors);
        validateRequiredAndLength(country, "country", "Country is required.", COUNTRY_MAX_LENGTH,
                "Country must be 100 characters or fewer.", fieldErrors);
        validateRequiredAndLength(postalCode, "postalCode", "Postal code is required.", POSTAL_CODE_MAX_LENGTH,
                "Postal code must be 20 characters or fewer.", fieldErrors);

        if (postalCode != null && !POSTAL_CODE_PATTERN.matcher(postalCode).matches()) {
            fieldErrors.put("postalCode", "Postal code format is invalid.");
        }

        if (region != null && region.length() > REGION_MAX_LENGTH) {
            fieldErrors.put("region", "Region must be 100 characters or fewer.");
        }

        if (!fieldErrors.isEmpty()) {
            throw new MediaLocationValidationException(
                    "Please provide a valid address including street, city, province/state, country, and postal code.",
                    fieldErrors);
        }

        mediaLocation.setStreet(street);
        mediaLocation.setCity(city);
        mediaLocation.setProvince(province);
        mediaLocation.setCountry(country);
        mediaLocation.setPostalCode(postalCode);
        mediaLocation.setRegion(region);
    }

    private void validateRequiredAndLength(String value,
            String key,
            String requiredMessage,
            int maxLength,
            String maxLengthMessage,
            Map<String, String> fieldErrors) {
        if (value == null) {
            fieldErrors.put(key, requiredMessage);
            return;
        }
        if (value.length() > maxLength) {
            fieldErrors.put(key, maxLengthMessage);
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
