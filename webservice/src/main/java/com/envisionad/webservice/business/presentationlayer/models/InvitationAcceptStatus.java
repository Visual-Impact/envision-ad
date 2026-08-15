package com.envisionad.webservice.business.presentationlayer.models;

/** Outcome of POST /businesses/{businessId}/employees (P5 FR 3.2). */
public enum InvitationAcceptStatus {
    /** Completed — the caller was authenticated and is now an employee. */
    ACCEPTED,
    /** No JWT, but the invitation's email already has an Auth0 account — the caller
     *  must log in and re-call this endpoint to actually complete the accept. */
    LOGIN_REQUIRED,
    /** No JWT and no existing account for the invitation's email — a new Auth0 user
     *  was provisioned and the employee row created; a password-change email was sent. */
    PROVISIONED
}
