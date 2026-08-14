package com.envisionad.webservice.admin.presentationlayer.models;

import com.envisionad.webservice.business.presentationlayer.models.BusinessResponseModel;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * brief FR 4.2 — 201 response for account creation. `warnings` is non-empty when the
 * DB write succeeded but a best-effort post-commit step (role assignment, password
 * ticket, welcome email) failed; the account still exists and is usable once fixed via
 * resend-credentials (FR 4.2.d).
 */
@Data
@NoArgsConstructor
public class AdminAccountResponseModel {
    private BusinessResponseModel business;
    private String ownerUserId;
    private List<String> warnings;
}
