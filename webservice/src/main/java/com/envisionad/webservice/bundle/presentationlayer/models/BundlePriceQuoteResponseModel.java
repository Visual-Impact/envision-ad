package com.envisionad.webservice.bundle.presentationlayer.models;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * The buyer-facing price preview for one bundle. A slim projection of
 * {@code BundlePriceQuote} — deliberately NOT the record itself, whose
 * {@code eligibleMedias} carries lazy relations and image bytes.
 */
@Data
@NoArgsConstructor
public class BundlePriceQuoteResponseModel {
    private int screenCount;
    private BigDecimal finalPrice;
    /** Shared per-screen price, or null when mixed/empty/partially-priceless. */
    private BigDecimal perScreenPrice;
}
