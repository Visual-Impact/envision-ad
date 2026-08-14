package com.envisionad.webservice.admin.presentationlayer.models;

import com.envisionad.webservice.business.dataaccesslayer.Roles;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** brief FR 4.5 — one row of GET /api/v1/admin/accounts. */
@Data
@NoArgsConstructor
public class AdminAccountListItemModel {
    private String businessId;
    private String name;
    private String ownerEmail;
    private Roles roles;
    private String businessTypeVenueId;
    private boolean active;
    private LocalDateTime dateCreated;
}
