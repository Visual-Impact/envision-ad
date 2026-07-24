package com.envisionad.webservice.bundle.businesslogiclayer;

import com.envisionad.webservice.media.DataAccessLayer.Media;

import java.util.List;

public interface MediaEligibilityFilter {
    // Returns the subset of candidateMedias eligible for this buyer.
    // Order matters: filters run in a fixed, explicit list (not @Order-scanned)
    // so P4/P8 insertion order is a one-line change, not implicit.
    List<Media> filter(List<Media> candidateMedias, PricingContext context);
}
