package com.envisionad.webservice.bundle.mappinglayer;

import com.envisionad.webservice.bundle.businesslogiclayer.BundlePriceQuote;
import com.envisionad.webservice.bundle.dataaccesslayer.Bundle;
import com.envisionad.webservice.bundle.presentationlayer.models.BundleCandidateMediaResponseModel;
import com.envisionad.webservice.bundle.presentationlayer.models.BundlePriceQuoteResponseModel;
import com.envisionad.webservice.bundle.presentationlayer.models.BundleResponseModel;
import com.envisionad.webservice.media.DataAccessLayer.Media;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Hand-written rather than MapStruct because the response carries computed extras
 * (the price quote and the blocking-subscription count) alongside the entity —
 * same reason {@code VenueResponseMapper} is hand-written for {@code mediaCount}.
 */
@Component
public class BundleResponseMapper {

    public BundleResponseModel entityToResponseModel(Bundle bundle,
            BundlePriceQuote quote,
            long activeSubscriptionCount) {
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
        response.setActive(bundle.isActive());
        response.setScreenCount(quote.eligibleMedias().size());
        response.setBasePrice(quote.basePrice());
        response.setPerScreenPrice(perScreenPrice(quote));
        response.setActiveSubscriptionCount(activeSubscriptionCount);
        return response;
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
