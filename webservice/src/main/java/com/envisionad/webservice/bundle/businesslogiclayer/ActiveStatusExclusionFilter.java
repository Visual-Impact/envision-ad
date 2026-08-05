package com.envisionad.webservice.bundle.businesslogiclayer;

import com.envisionad.webservice.media.DataAccessLayer.Media;
import com.envisionad.webservice.media.DataAccessLayer.Status;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Drops any media that is not ACTIVE.
 *
 * <p>Defensive by design: the rule-matching query already restricts to ACTIVE, but
 * the step is explicit here so the whole eligibility contract is readable in one
 * place rather than split between a Specification and this chain.
 */
@Component
public class ActiveStatusExclusionFilter implements MediaEligibilityFilter {

    @Override
    public List<Media> filter(List<Media> candidateMedias, PricingContext context) {
        return candidateMedias.stream()
                .filter(media -> media.getStatus() == Status.ACTIVE)
                .toList();
    }
}
