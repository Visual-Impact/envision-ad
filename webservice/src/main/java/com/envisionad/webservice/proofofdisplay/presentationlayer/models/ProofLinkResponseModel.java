package com.envisionad.webservice.proofofdisplay.presentationlayer.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The {@code proofLink} field on each row of {@code GET /api/v1/bundle-subscriptions}, pinned by
 * brief 12 as {@code { status, url }}. {@code url} is null unless {@code status} is {@code SET}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProofLinkResponseModel {
    private ProofLinkStatus status;
    private String url;
}
