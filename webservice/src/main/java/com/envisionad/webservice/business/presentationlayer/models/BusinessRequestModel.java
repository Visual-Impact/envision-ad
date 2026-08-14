package com.envisionad.webservice.business.presentationlayer.models;

import com.envisionad.webservice.business.dataaccesslayer.Address;
import com.envisionad.webservice.business.dataaccesslayer.OrganizationSize;
import com.envisionad.webservice.business.dataaccesslayer.Roles;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class BusinessRequestModel {
    private String name;
    private OrganizationSize organizationSize;
    private Address address;
    private Roles roles;

    // P4 hook (brief FR 2.2) — settable at creation via the admin form. Deliberately no
    // `active` field here: BusinessRequestModel is also used by updateBusinessById
    // (PUT /businesses/{id}, gated on the existing-employee `update:business` permission,
    // not `manage:accounts`), and that full-replace path would let a business owner
    // self-toggle `active` and silently defeat the admin-only deactivate gate (FR 5.5).
    // `active` is only ever set via AdminAccountService, never through this DTO.
    private String businessTypeVenueId;
}
