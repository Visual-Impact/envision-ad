package com.envisionad.webservice.bundle.businesslogiclayer;

public interface BundlePricingService {
    BundlePriceQuote quote(String bundleId, String advertiserBusinessId);
}
