package com.envisionad.webservice.admin.presentationlayer.models;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for GET /api/v1/admin/accounts/{businessId}/roles/removal-eligibility —
 * a read-only precheck the roles-edit UI calls before the admin tries to save, so it
 * can gray out the save action and explain why instead of surfacing the block only
 * after a failed PATCH. `true` means "removing this role right now would succeed";
 * it says nothing about whether the business currently holds the role at all.
 */
@Data
@NoArgsConstructor
public class RoleRemovalEligibilityResponseModel {
    private boolean mediaOwnerRemovable;
    private boolean advertiserRemovable;
}
