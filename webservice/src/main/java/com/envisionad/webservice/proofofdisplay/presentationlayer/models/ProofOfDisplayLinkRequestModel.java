package com.envisionad.webservice.proofofdisplay.presentationlayer.models;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Body of {@code PUT /api/v1/proof-of-display/subscriptions/{subscriptionId}/link}. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProofOfDisplayLinkRequestModel {
    @NotBlank
    private String url;
}
