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

    // Business type — honoured only by the admin create-account flow. This DTO is also the
    // body of the self-service PUT /businesses/{id} (`update:business`, any employee), and
    // updateBusinessById ignores the field there: it drives competitive exclusion, so an owner
    // must not change it.
    // Deliberately no `active` field at all — that is only ever set via AdminAccountService
    // (P5 D5).
    private String businessTypeVenueId;
}
