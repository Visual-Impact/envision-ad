package com.envisionad.webservice.admin.presentationlayer.models;

import com.envisionad.webservice.business.presentationlayer.models.BusinessRequestModel;
import lombok.Data;
import lombok.NoArgsConstructor;

/** brief FR 4.2 — email/name become the Auth0 identity, business carries the org details. */
@Data
@NoArgsConstructor
public class AdminAccountRequestModel {
    private String email;
    private String name;
    private BusinessRequestModel business;
}
