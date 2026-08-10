package com.envisionad.webservice.bundle.businesslogiclayer;

import com.envisionad.webservice.bundle.dataaccesslayer.Bundle;

public interface BundlePricingService {
    BundlePriceQuote quote(String bundleId, String advertiserBusinessId);

    /** Same pricing, for a caller that already holds the {@code Bundle} — skips the re-fetch. */
    BundlePriceQuote quote(Bundle bundle, String advertiserBusinessId);
}
