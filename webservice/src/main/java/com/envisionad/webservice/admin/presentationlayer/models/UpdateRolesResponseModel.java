package com.envisionad.webservice.admin.presentationlayer.models;

import com.envisionad.webservice.business.presentationlayer.models.BusinessResponseModel;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 200 response for PATCH /api/v1/admin/accounts/{businessId}/roles. `warnings` is
 * non-empty when the DB write succeeded but resyncing an employee's Auth0 roles
 * failed — same "partial success" posture as {@link AdminAccountResponseModel},
 * because this endpoint fans the change out to every employee of the business and a
 * single Auth0 call failing shouldn't roll back a role change that already committed
 * for everyone else. Retrying this same PATCH is the recovery path: the fan-out
 * always re-applies the full target role set to every employee rather than tracking a
 * delta, so it's safe to call again.
 */
@Data
@NoArgsConstructor
public class UpdateRolesResponseModel {
    private BusinessResponseModel business;
    private List<String> warnings;
}
