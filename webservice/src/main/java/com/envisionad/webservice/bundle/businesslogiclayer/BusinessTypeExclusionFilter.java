package com.envisionad.webservice.bundle.businesslogiclayer;

import com.envisionad.webservice.business.dataaccesslayer.Business;
import com.envisionad.webservice.business.dataaccesslayer.BusinessRepository;
import com.envisionad.webservice.media.DataAccessLayer.Media;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * P4 competitive exclusion: drops media whose venue is the buyer's own business type, so a
 * gym is never shown, counted or billed for a rival gym's screen.
 *
 * <p>Buyer-specific, so anonymous browsing (a null {@code advertiserBusinessId}) passes
 * straight through — and returns before touching the repository, because the public
 * listing quotes every bundle on every load.
 *
 * <p>Runs only on the browse/purchase path (P4 FR-6). A live subscription keeps the screen
 * set it was bought with; nothing re-runs this against existing subscription rows.
 */
@Component
public class BusinessTypeExclusionFilter implements MediaEligibilityFilter {

    private final BusinessRepository businessRepository;

    public BusinessTypeExclusionFilter(BusinessRepository businessRepository) {
        this.businessRepository = businessRepository;
    }

    @Override
    public List<Media> filter(List<Media> candidateMedias, PricingContext context) {
        if (candidateMedias.isEmpty() || context.advertiserBusinessId() == null) {
            return candidateMedias;
        }

        // Callers resolve the business before quoting (404 on an unknown id), so a miss here
        // is not reachable through the API; excluding nothing is the only safe reading.
        Business buyer = businessRepository.findByBusinessId_BusinessId(context.advertiserBusinessId());
        if (buyer == null || !hasBusinessType(buyer.getBusinessTypeVenueId())) {
            return candidateMedias;
        }

        String businessTypeVenueId = buyer.getBusinessTypeVenueId();
        return candidateMedias.stream()
                .filter(media -> !sharesBusinessType(businessTypeVenueId, media))
                .toList();
    }

    /**
     * The one definition of the rule, shared with P6's creative distribution so pricing and
     * email recipients can never disagree about which screens are a competitor's.
     *
     * <p>A business with no type is excluded from nothing, and a screen with no venue can
     * match no one's type (P4 FR-5).
     */
    public static boolean sharesBusinessType(String businessTypeVenueId, Media media) {
        return hasBusinessType(businessTypeVenueId) && businessTypeVenueId.equals(media.getVenueId());
    }

    private static boolean hasBusinessType(String businessTypeVenueId) {
        return businessTypeVenueId != null && !businessTypeVenueId.isBlank();
    }
}
