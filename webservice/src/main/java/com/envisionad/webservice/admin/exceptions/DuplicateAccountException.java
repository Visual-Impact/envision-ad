package com.envisionad.webservice.admin.exceptions;

/** brief FR 5.1 — an Auth0 identity already exists for the requested email. */
public class DuplicateAccountException extends RuntimeException {
    public DuplicateAccountException(String email) {
        super("An account with this email already exists: " + email);
    }
}
