package com.envisionad.webservice.proofofdisplay.presentationlayer.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/** The link as saved, returned by the {@code PUT}. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProofOfDisplayLinkResponseModel {
    private String subscriptionId;
    private String url;
    /** With an offset, so the browser formats it in the viewer's own time zone. */
    private OffsetDateTime updatedAt;
}
