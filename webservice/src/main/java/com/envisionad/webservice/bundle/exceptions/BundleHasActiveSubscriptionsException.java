package com.envisionad.webservice.bundle.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * A bundle with live subscriptions (INCOMPLETE/ACTIVE/PAST_DUE) cannot be deleted.
 * Canceled-only history is deletable and cascades with the bundle.
 */
@ResponseStatus(code = HttpStatus.CONFLICT, reason = "Bundle has active subscriptions")
public class BundleHasActiveSubscriptionsException extends RuntimeException {
    public BundleHasActiveSubscriptionsException(String bundleId, long subscriptionCount) {
        super("Bundle " + bundleId + " cannot be deleted: it has "
                + subscriptionCount + " active subscription(s).");
    }
}
