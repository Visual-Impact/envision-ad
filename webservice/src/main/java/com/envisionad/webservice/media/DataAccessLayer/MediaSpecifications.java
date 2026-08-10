package com.envisionad.webservice.media.DataAccessLayer;

import com.envisionad.webservice.media.DataAccessLayer.Media;
import com.envisionad.webservice.media.DataAccessLayer.Status;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class MediaSpecifications {

    public static Specification<Media> hasStatus(Status status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Media> titleContains(String title) {
        return (root, query, cb) -> {
            if (title == null || title.isBlank()) {
                return null;
            }
            return cb.like(cb.lower(root.get("title")), "%" + title.toLowerCase() + "%");
        };
    }

    public static Specification<Media> priceBetween(BigDecimal minPrice, BigDecimal maxPrice) {
        return (root, query, cb) -> {
            if (minPrice == null && maxPrice == null) {
                return null;
            }

            if (minPrice != null && maxPrice != null) {
                return cb.between(root.get("price"), minPrice, maxPrice);
            }

            if (minPrice != null) {
                return cb.greaterThanOrEqualTo(root.get("price"), minPrice);
            }

            return cb.lessThanOrEqualTo(root.get("price"), maxPrice);
        };
    }

    public static Specification<Media> weeklyImpressionsGreaterThan(Integer minWeeklyImpressions) {
        return (root, query, cb) -> {
            if (minWeeklyImpressions == null) {
                return null;
            }

            Expression<Integer> weekly = cb.prod(cb.coalesce(
                root.get("dailyImpressions"), 0),
                cb.coalesce(root.get("activeDays"), 0)
            );

            return cb.greaterThanOrEqualTo(weekly, minWeeklyImpressions);
        };
    }


    public static Specification<Media> businessIdEquals(UUID businessId) {
        return (root, query, cb) -> businessId == null ? null
                : cb.equal(root.get("businessId"), businessId);
    }

    public static Specification<Media> mediaIdIsNotEqual(UUID excludedId) {
        return (root, query, cb) -> excludedId == null ? null
                : cb.notEqual(root.get("id"), excludedId);
    }

    public static Specification<Media> venueIdIn(List<String> venueIds) {
        return (root, query, cb) -> {
            if (venueIds == null || venueIds.isEmpty()) return null;
            return root.get("venueId").in(venueIds);
        };
    }

    public static Specification<Media> venueIdEquals(String venueId) {
        return (root, query, cb) -> venueId == null || venueId.isBlank() ? null
                : cb.equal(root.get("venueId"), venueId.trim());
    }

    /**
     * Bundle CITY rule matching: compares against the media's location city
     * case-insensitively and ignoring surrounding whitespace on both sides, since
     * neither value comes from a controlled taxonomy.
     */
    public static Specification<Media> cityEqualsIgnoreCase(String city) {
        return locationFieldEqualsIgnoreCase("city", city);
    }

    /** Bundle REGION rule matching — same semantics as {@link #cityEqualsIgnoreCase}. */
    public static Specification<Media> regionEqualsIgnoreCase(String region) {
        return locationFieldEqualsIgnoreCase("region", region);
    }

    private static Specification<Media> locationFieldEqualsIgnoreCase(String field, String value) {
        return (root, query, cb) -> {
            if (value == null || value.isBlank()) {
                return null;
            }
            Join<Media, MediaLocation> location = root.join("mediaLocation", JoinType.INNER);
            return cb.equal(
                    cb.lower(cb.trim(location.get(field))),
                    value.trim().toLowerCase(java.util.Locale.ROOT));
        };
    }

    /**
     * Eager-fetches {@code mediaLocation} (a {@code @ManyToOne}, so this can never
     * multiply rows) so callers that read city/region per row don't lazy-load it once
     * per {@code Media} — see {@code BundleServiceImpl.getRuleMatchedMedias}. AND-able
     * onto any other spec; skipped for a count query since a fetch there is meaningless
     * and some JPA providers reject it.
     */
    public static Specification<Media> fetchMediaLocation() {
        return (root, query, cb) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("mediaLocation", JoinType.LEFT);
            }
            return cb.conjunction();
        };
    }

    public static Specification<Media> withinBounds(List<Double> bounds) {
        return (root, query, cb) -> {
            if (bounds == null || bounds.size() != 4) {
                return null;
            }

            double south = bounds.get(0);
            double north = bounds.get(1);
            double west = bounds.get(2);
            double east = bounds.get(3);

            double minLat = Math.min(south, north);
            double maxLat = Math.max(south, north);

            Join<Media, MediaLocation> location = root.join("mediaLocation", JoinType.INNER);

            Predicate latPredicate =
                cb.between(location.get("latitude"), minLat, maxLat);

            Predicate lngPredicate;

            if (west <= east) {
                // Normal case: bounding box does not cross the International Date Line.
                lngPredicate = cb.between(location.get("longitude"), west, east);
            } else {
                // Bounding box crosses the International Date Line. Select longitudes
                // greater than or equal to west OR less than or equal to east.
                Predicate westToDateLine = cb.greaterThanOrEqualTo(location.get("longitude"), west);
                Predicate dateLineToEast = cb.lessThanOrEqualTo(location.get("longitude"), east);
                lngPredicate = cb.or(westToDateLine, dateLineToEast);
            }
            return cb.and(latPredicate, lngPredicate);
        };
    }
}
