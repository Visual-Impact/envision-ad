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

import java.text.Normalizer;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
    private static final String COUNTRY_FIELD = "country";
    private static final String PROVINCE_FIELD = "province";
    private static final String CITY_FIELD = "city";
    private static final String STREET_FIELD = "street";
    private static final String POSTAL_CODE_FIELD = "postalCode";
    private static final String INVALID_ADDRESS_ERROR = "Address could not be verified.";
    private static final String COORDINATE_EXTRACTION_ERROR = "Address was matched but coordinates could not be determined. Please check street, city, province/state, country, and postal code.";
    private static final String GEOCODING_UNAVAILABLE_ERROR = "Address validation service is temporarily unavailable. Please try again shortly.";
    private static final String LOCATION_DELETE_BLOCKED_MEDIA_ERROR = "Cannot delete media location while active, pending, or rejected media are assigned. Please delete those media first.";
    private static final Map<String, String> ADDRESS_VERIFICATION_ERRORS = Map.of(
            STREET_FIELD, "Verify the street name or number.",
            CITY_FIELD, "Verify the city value.",
            PROVINCE_FIELD, "Verify the province/state value.",
            COUNTRY_FIELD, "Verify the country value.",
            POSTAL_CODE_FIELD, "Verify the postal code value.");

    private final MediaLocationRepository mediaLocationRepository;
    private final BusinessService businessService;
    private final GeocodingService geocodingService;
    private final ObjectMapper objectMapper;

    private record GeocodingLookupResult(String query, String jsonResponse) {
    }

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

        GeocodingLookupResult lookupResult = geocodeWithFallbacks(
                mediaLocation.getStreet(),
                mediaLocation.getCity(),
                mediaLocation.getProvince(),
                mediaLocation.getCountry(),
                mediaLocation.getPostalCode());
        String address = lookupResult.query();
        String jsonResponse = lookupResult.jsonResponse();

        mediaLocation.setGeocodingResponse(jsonResponse);

        try {
            JsonNode firstResult = readFirstResult(jsonResponse)
                    .orElseThrow(() -> new IllegalStateException(COORDINATE_EXTRACTION_ERROR));
            if (!firstResult.has("lat") || !firstResult.has("lon")) {
                throw new IllegalStateException(COORDINATE_EXTRACTION_ERROR);
            }

            validateGeocodedAddressConsistency(mediaLocation, firstResult);

            mediaLocation.setLatitude(Double.parseDouble(firstResult.get("lat").asText()));
            mediaLocation.setLongitude(Double.parseDouble(firstResult.get("lon").asText()));
        } catch (MediaLocationValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error extracting coordinates from geocoding response for address '{}': {}", address,
                    e.getMessage(), e);
            throw new MediaLocationValidationException(COORDINATE_EXTRACTION_ERROR, ADDRESS_VERIFICATION_ERRORS, e);
        }
    }

    private GeocodingLookupResult geocodeWithFallbacks(String street,
            String city,
            String province,
            String country,
            String postalCode) {
        List<String> candidates = buildAddressCandidates(street, city, province, country, postalCode);

        for (String candidate : candidates) {
            try {
                var response = geocodingService.geocodeAddress(candidate);
                if (response.isPresent()) {
                    return new GeocodingLookupResult(candidate, response.get());
                }
            } catch (GeocodingServiceUnavailableException e) {
                log.error("Geocoding service unavailable for address '{}': {}", candidate, e.getMessage(), e);
                throw new GeocodingServiceUnavailableException(GEOCODING_UNAVAILABLE_ERROR, e);
            }
        }

        log.info("Address verification returned no geocoding match for candidates: {}", candidates);
        Map<String, String> pinpointErrors = inferAddressFieldErrors(
                street,
                city,
                province,
                country,
                postalCode,
                GEOCODING_UNAVAILABLE_ERROR);
        throw new MediaLocationValidationException(
                buildAddressVerificationMessage(INVALID_ADDRESS_ERROR, pinpointErrors),
                pinpointErrors);
    }

    private Optional<JsonNode> readFirstResult(String jsonResponse) throws Exception {
        JsonNode rootNode = objectMapper.readTree(jsonResponse);
        if (!rootNode.isArray() || rootNode.size() == 0) {
            return Optional.empty();
        }
        return Optional.of(rootNode.get(0));
    }

    private List<String> buildAddressCandidates(String street, String city, String province, String country, String postalCode) {
        Set<String> candidates = new LinkedHashSet<>();

        // Full address including postal code first.
        candidates.add(String.format("%s, %s, %s, %s, %s", street, city, province, country, postalCode));
        candidates.add(String.format("%s, %s, %s, %s, %s", street, city, province, postalCode, country));

        // Common fallback shapes for geocoders that choke on one component.
        candidates.add(String.format("%s, %s, %s, %s", street, city, province, country));
        candidates.add(String.format("%s, %s, %s, %s", street, city, postalCode, country));
        candidates.add(String.format("%s, %s, %s", street, city, country));

        String compactPostalCode = postalCode.replaceAll("[\\s-]", "");
        if (!compactPostalCode.equals(postalCode)) {
            candidates.add(String.format("%s, %s, %s, %s, %s", street, city, province, country, compactPostalCode));
            candidates.add(String.format("%s, %s, %s, %s", street, city, compactPostalCode, country));
        }

        return new ArrayList<>(candidates);
    }

    private Map<String, String> inferAddressFieldErrors(String street,
            String city,
            String province,
            String country,
            String postalCode,
            String geocodingUnavailableError) {
        List<Map.Entry<String, String>> diagnosticQueries = List.of(
                Map.entry(COUNTRY_FIELD, String.format("%s, %s, %s, %s", street, city, province, postalCode)),
                Map.entry(PROVINCE_FIELD, String.format("%s, %s, %s, %s", street, city, country, postalCode)),
                Map.entry(CITY_FIELD, String.format("%s, %s, %s, %s", street, province, country, postalCode)),
                Map.entry(POSTAL_CODE_FIELD, String.format("%s, %s, %s, %s", street, city, province, country)),
                Map.entry(STREET_FIELD, String.format("%s, %s, %s, %s", city, province, country, postalCode)));

        for (Map.Entry<String, String> diagnostic : diagnosticQueries) {
            if (hasGeocodingMatch(diagnostic.getValue(), geocodingUnavailableError)) {
                String field = diagnostic.getKey();
                return Map.of(field, ADDRESS_VERIFICATION_ERRORS.get(field));
            }
        }

        Map<String, String> referenceMismatches = inferFieldErrorsFromReferenceLookup(
                street,
                city,
                province,
                country,
                postalCode,
                geocodingUnavailableError);
        if (!referenceMismatches.isEmpty()) {
            return referenceMismatches;
        }

        return ADDRESS_VERIFICATION_ERRORS;
    }

    private boolean hasGeocodingMatch(String query, String geocodingUnavailableError) {
        try {
            return geocodingService.geocodeAddress(query).isPresent();
        } catch (GeocodingServiceUnavailableException e) {
            log.error("Geocoding service unavailable for diagnostic query '{}': {}", query, e.getMessage(), e);
            throw new GeocodingServiceUnavailableException(geocodingUnavailableError, e);
        }
    }

    private Map<String, String> inferFieldErrorsFromReferenceLookup(String street,
            String city,
            String province,
            String country,
            String postalCode,
            String geocodingUnavailableError) {
        List<String> referenceQueries = List.of(
                String.format("%s, %s", postalCode, country),
                String.format("%s, %s, %s", street, postalCode, country),
                String.format("%s, %s, %s", street, province, country),
                String.format("%s, %s, %s", city, province, country));

        for (String query : referenceQueries) {
            var reference = lookupFirstGeocodingResult(query, geocodingUnavailableError);
            if (reference.isEmpty()) {
                continue;
            }

            JsonNode addressNode = reference.get().path("address");
            if (addressNode == null || !addressNode.isObject()) {
                continue;
            }

            Map<String, String> mismatches = collectAddressMismatches(street, city, province, country, postalCode,
                    addressNode);
            if (!mismatches.isEmpty()) {
                return mismatches;
            }
        }

        return Map.of();
    }

    private Optional<JsonNode> lookupFirstGeocodingResult(String query,
            String geocodingUnavailableError) {
        try {
            var response = geocodingService.geocodeAddress(query);
            if (response.isEmpty()) {
                return Optional.empty();
            }
            return readFirstResult(response.get());
        } catch (GeocodingServiceUnavailableException e) {
            log.error("Geocoding service unavailable for diagnostic query '{}': {}", query, e.getMessage(), e);
            throw new GeocodingServiceUnavailableException(geocodingUnavailableError, e);
        } catch (Exception e) {
            log.debug("Could not parse geocoding response for diagnostic query '{}': {}", query, e.getMessage());
            return Optional.empty();
        }
    }

    private String buildAddressVerificationMessage(String baseMessage, Map<String, String> fieldErrors) {
        if (fieldErrors == null || fieldErrors.isEmpty()) {
            return baseMessage;
        }
        if (fieldErrors.size() == 1) {
            String fieldName = toReadableFieldName(fieldErrors.keySet().iterator().next());
            return String.format("%s Please verify the %s.", baseMessage, fieldName);
        }
        String fieldList = fieldErrors.keySet().stream()
                .map(this::toReadableFieldName)
                .collect(Collectors.joining(", "));
        return String.format("%s Please verify: %s.", baseMessage, fieldList);
    }

    private String toReadableFieldName(String field) {
        return switch (field) {
            case COUNTRY_FIELD -> "country";
            case PROVINCE_FIELD -> "province/state";
            case CITY_FIELD -> "city";
            case STREET_FIELD -> "street";
            case POSTAL_CODE_FIELD -> "postal code";
            default -> field;
        };
    }

    private Map<String, String> collectAddressMismatches(String street,
            String city,
            String province,
            String country,
            String postalCode,
            JsonNode addressNode) {
        Map<String, String> mismatches = new LinkedHashMap<>();

        if (!matchesCountry(country, addressNode)) {
            mismatches.put(COUNTRY_FIELD, ADDRESS_VERIFICATION_ERRORS.get(COUNTRY_FIELD));
        }
        if (!matchesProvince(province, addressNode)) {
            mismatches.put(PROVINCE_FIELD, ADDRESS_VERIFICATION_ERRORS.get(PROVINCE_FIELD));
        }
        if (!matchesCity(city, addressNode)) {
            mismatches.put(CITY_FIELD, ADDRESS_VERIFICATION_ERRORS.get(CITY_FIELD));
        }
        if (!matchesPostalCode(postalCode, addressNode)) {
            mismatches.put(POSTAL_CODE_FIELD, ADDRESS_VERIFICATION_ERRORS.get(POSTAL_CODE_FIELD));
        }
        if (!matchesStreet(street, addressNode)) {
            mismatches.put(STREET_FIELD, ADDRESS_VERIFICATION_ERRORS.get(STREET_FIELD));
        }

        return mismatches;
    }

    private void validateGeocodedAddressConsistency(MediaLocation mediaLocation, JsonNode geocodingResult) {
        JsonNode addressNode = geocodingResult.path("address");
        if (addressNode == null || !addressNode.isObject()) {
            return;
        }

        Map<String, String> mismatchedFields = collectAddressMismatches(
                mediaLocation.getStreet(),
                mediaLocation.getCity(),
                mediaLocation.getProvince(),
                mediaLocation.getCountry(),
                mediaLocation.getPostalCode(),
                addressNode);

        if (!mismatchedFields.isEmpty()) {
            throw new MediaLocationValidationException(
                    buildAddressVerificationMessage(INVALID_ADDRESS_ERROR, mismatchedFields),
                    mismatchedFields);
        }
    }

    private boolean matchesCountry(String expectedCountry, JsonNode addressNode) {
        String expected = normalizeComparable(expectedCountry);
        String country = normalizeComparable(addressNode.path("country").asText(null));
        String countryCode = normalizeComparable(addressNode.path("country_code").asText(null));
        if (country != null && country.equals(expected)) {
            return true;
        }
        return countryCode != null && countryCode.equals(expected);
    }

    private boolean matchesProvince(String expectedProvince, JsonNode addressNode) {
        String expected = normalizeComparable(expectedProvince);
        if (expected == null) {
            return false;
        }

        List<String> candidates = new ArrayList<>();
        candidates.add(addressNode.path("state").asText(null));
        candidates.add(addressNode.path("province").asText(null));
        candidates.add(addressNode.path("region").asText(null));
        candidates.add(addressNode.path("state_district").asText(null));
        candidates.add(extractSubdivisionCode(addressNode.path("ISO3166-2-lvl4").asText(null)));
        candidates.add(extractSubdivisionCode(addressNode.path("ISO3166-2-lvl6").asText(null)));

        return anyCandidateMatches(expected, candidates, false);
    }

    private boolean matchesCity(String expectedCity, JsonNode addressNode) {
        String expected = normalizeComparable(expectedCity);
        if (expected == null) {
            return false;
        }
        List<String> candidates = new ArrayList<>();
        candidates.add(addressNode.path("city").asText(null));
        candidates.add(addressNode.path("town").asText(null));
        candidates.add(addressNode.path("village").asText(null));
        candidates.add(addressNode.path("municipality").asText(null));
        candidates.add(addressNode.path("suburb").asText(null));
        candidates.add(addressNode.path("city_district").asText(null));
        candidates.add(addressNode.path("hamlet").asText(null));
        candidates.add(addressNode.path("county").asText(null));
        return anyCandidateMatches(expected, candidates, true);
    }

    private boolean matchesPostalCode(String expectedPostalCode, JsonNode addressNode) {
        String expected = normalizePostalComparable(expectedPostalCode);
        String actual = normalizePostalComparable(addressNode.path("postcode").asText(null));
        if (actual == null) {
            return true;
        }
        return expected.equals(actual);
    }

    private boolean matchesStreet(String expectedStreet, JsonNode addressNode) {
        Set<String> expectedTokens = normalizeStreetTokens(expectedStreet);
        if (expectedTokens.isEmpty()) {
            return false;
        }

        String road = addressNode.path("road").asText(null);
        String houseNumber = addressNode.path("house_number").asText(null);

        List<String> candidates = new ArrayList<>();
        candidates.add(road);
        if (road != null && houseNumber != null) {
            candidates.add(houseNumber + " " + road);
            candidates.add(road + " " + houseNumber);
        }

        // If geocoder does not provide street details, do not fail street consistency.
        if (candidates.stream().allMatch(v -> v == null || v.isBlank())) {
            return true;
        }

        String expectedNumber = extractNumericToken(expectedTokens);
        for (String candidate : candidates) {
            Set<String> candidateTokens = normalizeStreetTokens(candidate);
            if (candidateTokens.isEmpty()) {
                continue;
            }

            String candidateNumber = extractNumericToken(candidateTokens);
            if (expectedNumber != null && candidateNumber != null && !expectedNumber.equals(candidateNumber)) {
                continue;
            }

            Set<String> expectedWithoutNumber = removeNumericTokens(expectedTokens);
            Set<String> candidateWithoutNumber = removeNumericTokens(candidateTokens);
            if (!expectedWithoutNumber.isEmpty()) {
                for (String token : expectedWithoutNumber) {
                    if (candidateWithoutNumber.contains(token)) {
                        return true;
                    }
                }
            }
        }

        // Fallback to normalized string comparison as a final permissive check.
        String expected = normalizeComparable(expectedStreet);
        return anyCandidateMatches(expected, candidates, true);
    }

    private boolean anyCandidateMatches(String expectedNormalized, List<String> candidates, boolean allowPartialMatch) {
        for (String candidate : candidates) {
            String normalizedCandidate = normalizeComparable(candidate);
            if (normalizedCandidate == null) {
                continue;
            }
            if (normalizedCandidate.equals(expectedNormalized)) {
                return true;
            }
            if (allowPartialMatch &&
                    (normalizedCandidate.contains(expectedNormalized) || expectedNormalized.contains(normalizedCandidate))) {
                return true;
            }
        }
        return false;
    }

    private String extractSubdivisionCode(String isoSubdivision) {
        if (isoSubdivision == null || !isoSubdivision.contains("-")) {
            return isoSubdivision;
        }
        return isoSubdivision.substring(isoSubdivision.indexOf('-') + 1);
    }

    private String normalizeComparable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{Alnum}]", "");
        return normalized.isEmpty() ? null : normalized;
    }

    private Set<String> normalizeStreetTokens(String value) {
        if (value == null) {
            return Set.of();
        }

        String tokenized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{Alnum}]", " ")
                .trim();
        if (tokenized.isEmpty()) {
            return Set.of();
        }

        Set<String> tokens = new LinkedHashSet<>();
        for (String rawToken : tokenized.split("\\s+")) {
            String token = canonicalizeStreetToken(rawToken);
            if (token == null || token.isBlank()) {
                continue;
            }
            tokens.add(token);
        }
        return tokens;
    }

    private String canonicalizeStreetToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return switch (token) {
            // street type variants
            case "street", "st", "rue", "road", "rd", "avenue", "ave", "av", "boulevard", "blvd", "boul", "chemin",
                    "ch", "drive", "dr", "lane", "ln", "court", "ct", "place", "pl", "terrace", "ter" -> null;
            // directions in EN/FR
            case "north", "n", "nord", "south", "s", "sud", "east", "e", "est", "west", "w", "ouest", "o" -> null;
            default -> token;
        };
    }

    private String extractNumericToken(Set<String> tokens) {
        for (String token : tokens) {
            if (token.chars().allMatch(Character::isDigit)) {
                return token;
            }
        }
        return null;
    }

    private Set<String> removeNumericTokens(Set<String> tokens) {
        Set<String> filtered = new LinkedHashSet<>();
        for (String token : tokens) {
            if (!token.chars().allMatch(Character::isDigit)) {
                filtered.add(token);
            }
        }
        return filtered;
    }

    private String normalizePostalComparable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase().replaceAll("[^A-Z0-9]", "");
        return normalized.isEmpty() ? null : normalized;
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
