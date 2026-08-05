package com.envisionad.webservice.bundle.businesslogiclayer;

import com.envisionad.webservice.media.DataAccessLayer.Media;

import java.math.BigDecimal;
import java.util.List;

public record BundlePriceQuote(List<Media> eligibleMedias, BigDecimal basePrice, BigDecimal finalPrice) {}
