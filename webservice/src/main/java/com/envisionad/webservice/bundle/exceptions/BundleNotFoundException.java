package com.envisionad.webservice.bundle.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.NOT_FOUND, reason = "Bundle not found")
public class BundleNotFoundException extends RuntimeException {
    public BundleNotFoundException(String bundleId) {
        super("Bundle with ID " + bundleId + " not found.");
    }
}
