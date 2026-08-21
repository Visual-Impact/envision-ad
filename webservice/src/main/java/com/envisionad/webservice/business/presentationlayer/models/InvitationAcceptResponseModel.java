package com.envisionad.webservice.business.presentationlayer.models;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class InvitationAcceptResponseModel {
    private InvitationAcceptStatus status;

    /** Set for ACCEPTED and PROVISIONED; null for LOGIN_REQUIRED. */
    private EmployeeResponseModel employee;
}
