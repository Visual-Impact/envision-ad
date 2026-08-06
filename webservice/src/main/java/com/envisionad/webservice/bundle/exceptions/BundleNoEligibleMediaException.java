package com.envisionad.webservice.bundle.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * A bundle whose eligible screen set is empty (or prices out at zero) cannot be
 * subscribed to. Recomputed server-side at subscribe time, so this fires even when the
 * discovery card was rendered while the bundle still had screens.
 */
@ResponseStatus(code = HttpStatus.CONFLICT, reason = "Bundle has no eligible screens")
public class BundleNoEligibleMediaException extends RuntimeException {
    public BundleNoEligibleMediaException(String bundleId) {
        super("Bundle " + bundleId + " has no eligible screens and cannot be subscribed to.");
    }
}
