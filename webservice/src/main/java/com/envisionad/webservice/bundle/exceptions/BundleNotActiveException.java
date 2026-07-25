package com.envisionad.webservice.bundle.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * A deactivated bundle is hidden from discovery and cannot take new subscribers (P1
 * brief req. 4). Existing subscriptions to it are unaffected and keep renewing.
 */
@ResponseStatus(code = HttpStatus.CONFLICT, reason = "Bundle is not active")
public class BundleNotActiveException extends RuntimeException {
    public BundleNotActiveException(String bundleId) {
        super("Bundle " + bundleId + " is not active and cannot be subscribed to.");
    }
}
