package com.envisionad.webservice.business.presentationlayer.models;

import com.envisionad.webservice.business.dataaccesslayer.Address;
import com.envisionad.webservice.business.dataaccesslayer.OrganizationSize;
import com.envisionad.webservice.business.dataaccesslayer.Roles;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class BusinessResponseModel {
    private String businessId;
    private String name;
    private String ownerId;
    private OrganizationSize organizationSize;
    private Address address;
    private Roles roles;
    private boolean verified;
    private String businessTypeVenueId;
    private boolean active;
    private LocalDateTime dateCreated;
}