package com.envisionad.webservice.admin.presentationlayer.models;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * P4 — PATCH /api/v1/admin/accounts/{businessId}/business-type body. A venue id from the
 * existing venue taxonomy; null (or blank) clears the business type.
 */
@Data
@NoArgsConstructor
public class UpdateBusinessTypeRequestModel {
    private String businessTypeVenueId;
}
