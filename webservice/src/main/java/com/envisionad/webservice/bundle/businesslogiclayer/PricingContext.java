package com.envisionad.webservice.bundle.businesslogiclayer;

/**
 * Everything the eligibility/pricing pipeline knows about who is asking.
 *
 * <p>{@code advertiserBusinessId} is null for anonymous browsing. It is threaded
 * through even though no v1 filter reads it, so that P4 (competitive exclusion) and
 * P8 (sold-out medias) become backend-only changes later.
 */
public record PricingContext(String bundleId, String advertiserBusinessId) {}
