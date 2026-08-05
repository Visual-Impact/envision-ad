package com.envisionad.webservice.bundle.presentationlayer.models;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One row of a bundle's rule-matched media set, for the admin exclusions modal.
 * Excluded media are flagged rather than omitted, so the admin can re-include them.
 */
@Data
@NoArgsConstructor
public class BundleCandidateMediaResponseModel {
    private UUID mediaId;
    private String title;
    private String mediaOwnerName;
    private String city;
    private String region;
    private String venueId;
    private BigDecimal price;
    private boolean excluded;
}
