package com.envisionad.webservice.admin.presentationlayer.models;

import lombok.Data;
import lombok.NoArgsConstructor;

/** brief FR 4.4 — PATCH /api/v1/admin/accounts/{businessId}/active body. */
@Data
@NoArgsConstructor
public class SetActiveRequestModel {
    private boolean active;
}
