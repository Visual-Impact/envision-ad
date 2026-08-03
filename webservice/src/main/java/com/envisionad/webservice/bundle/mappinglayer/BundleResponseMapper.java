package com.envisionad.webservice.bundle.mappinglayer;

import com.envisionad.webservice.bundle.businesslogiclayer.BundlePriceQuote;
import com.envisionad.webservice.bundle.dataaccesslayer.Bundle;
import com.envisionad.webservice.bundle.dataaccesslayer.BundleRuleType;
import com.envisionad.webservice.bundle.presentationlayer.models.BundleCandidateMediaResponseModel;
import com.envisionad.webservice.bundle.presentationlayer.models.BundlePriceQuoteResponseModel;
import com.envisionad.webservice.bundle.presentationlayer.models.BundleResponseModel;
import com.envisionad.webservice.media.DataAccessLayer.Media;
import com.envisionad.webservice.venue.dataaccesslayer.Venue;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Hand-written rather than MapStruct because the response carries computed extras
 * (the price quote and the blocking-subscription count) alongside the entity —
 * same reason {@code VenueResponseMapper} is hand-written for {@code mediaCount}.
 */
@Component
public class BundleResponseMapper {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /**
     * @param ruleVenue the venue a VENUE-rule bundle points at, or null — which means
     *                  either "not a VENUE bundle" or "that venue row is gone". Both
     *                  fall back to the raw rule value for the display label.
     */
    public BundleResponseModel entityToResponseModel(Bundle bundle,
            BundlePriceQuote quote,
            long activeSubscriptionCount,
            Venue ruleVenue) {
        BundleResponseModel response = new BundleResponseModel();
        response.setBundleId(bundle.getBundleId());
        response.setNameEn(bundle.getNameEn());
        response.setNameFr(bundle.getNameFr());
        response.setDescriptionEn(bundle.getDescriptionEn());
        response.setDescriptionFr(bundle.getDescriptionFr());
        response.setIdealForEn(bundle.getIdealForEn());
        response.setIdealForFr(bundle.getIdealForFr());
        response.setBadgeColor(bundle.getBadgeColor());
        response.setRuleType(bundle.getRuleType());
        response.setRuleValue(bundle.getRuleValue());
        response.setRuleValueLabelEn(ruleValueLabel(bundle, ruleVenue, Venue::getNameEn));
        response.setRuleValueLabelFr(ruleValueLabel(bundle, ruleVenue, Venue::getNameFr));
        response.setActive(bundle.isActive());
        response.setScreenCount(quote.eligibleMedias().size());
        // basePrice is the undiscounted sum and finalPrice is what the advertiser
        // pays; they only diverge once a bundle carries a discount, which is exactly
        // what the card's "was / now" comparison needs.
        response.setBasePrice(quote.basePrice());
        response.setFinalPrice(quote.finalPrice());
        response.setDiscountPercent(bundle.getDiscountPercent());
        response.setPerScreenPrice(perScreenPrice(quote));
        response.setDiscountedPerScreenPrice(
                discountedPerScreenPrice(perScreenPrice(quote), bundle.getDiscountPercent()));
        response.setActiveSubscriptionCount(activeSubscriptionCount);
        return response;
    }

    /**
     * The rule value as a human would read it, in one language. CITY and REGION are
     * free text on the media row with nothing to translate, so both languages get the
     * same string back — leaving the French one null would blank the rule cell for a
     * French admin, which is the whole bug this field exists to fix. Only VENUE has a
     * per-language name to resolve, and only when the venue row still exists.
     */
    private String ruleValueLabel(Bundle bundle, Venue ruleVenue, Function<Venue, String> venueName) {
        if (bundle.getRuleValue() == null) {
            return null;
        }
        if (bundle.getRuleType() == BundleRuleType.VENUE && ruleVenue != null) {
            return venueName.apply(ruleVenue);
        }
        return bundle.getRuleValue();
    }

    /**
     * The per-screen figure after the bundle's discount, or null when there is no
     * discount or no honest per-screen price to discount. Computed from the same
     * percentage the pricing pipeline applies, so the card's per-screen comparison
     * can never drift from the headline total.
     */
    private BigDecimal discountedPerScreenPrice(BigDecimal perScreenPrice, int discountPercent) {
        if (perScreenPrice == null || discountPercent <= 0) {
            return null;
        }
        BigDecimal multiplier = HUNDRED.subtract(BigDecimal.valueOf(discountPercent))
                .divide(HUNDRED, 4, RoundingMode.HALF_UP);
        return perScreenPrice.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * The single per-screen price when every eligible screen shares the same non-null
     * price, else null. Lets the discovery card show "$X × N screens" only when that
     * figure is honest; an empty set, a mixed-price set, or any priceless screen all
     * fall back to "N screens". Compared by value so 4.00 and 4.0 count as equal.
     */
    private BigDecimal perScreenPrice(BundlePriceQuote quote) {
        List<Media> eligible = quote.eligibleMedias();
        if (eligible.isEmpty() || eligible.stream().anyMatch(m -> m.getPrice() == null)) {
            return null;
        }
        boolean uniform = eligible.stream()
                .map(Media::getPrice)
                .map(BigDecimal::stripTrailingZeros)
                .distinct()
                .count() == 1;
        return uniform ? eligible.get(0).getPrice() : null;
    }

    public BundlePriceQuoteResponseModel quoteToResponseModel(BundlePriceQuote quote) {
        BundlePriceQuoteResponseModel response = new BundlePriceQuoteResponseModel();
        response.setScreenCount(quote.eligibleMedias().size());
        response.setFinalPrice(quote.finalPrice());
        response.setPerScreenPrice(perScreenPrice(quote));
        return response;
    }

    public BundleCandidateMediaResponseModel mediaToCandidateResponseModel(Media media,
            Set<UUID> excludedMediaIds) {
        BundleCandidateMediaResponseModel response = new BundleCandidateMediaResponseModel();
        response.setMediaId(media.getId());
        response.setTitle(media.getTitle());
        response.setMediaOwnerName(media.getMediaOwnerName());
        if (media.getMediaLocation() != null) {
            response.setCity(media.getMediaLocation().getCity());
            response.setRegion(media.getMediaLocation().getRegion());
        }
        response.setVenueId(media.getVenueId());
        response.setPrice(media.getPrice());
        response.setExcluded(excludedMediaIds.contains(media.getId()));
        return response;
    }

    public List<BundleCandidateMediaResponseModel> mediaListToCandidateResponseModelList(
            List<Media> medias, Set<UUID> excludedMediaIds) {
        return medias.stream()
                .map(media -> mediaToCandidateResponseModel(media, excludedMediaIds))
                .toList();
    }
}
