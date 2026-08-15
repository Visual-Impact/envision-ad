package com.envisionad.webservice.business.presentationlayer.models;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class InvitationRequestModel {
    private String email;

    // Optional — only used if accept-time provisioning is needed (P5 FR 3.2).
    private String name;
}
