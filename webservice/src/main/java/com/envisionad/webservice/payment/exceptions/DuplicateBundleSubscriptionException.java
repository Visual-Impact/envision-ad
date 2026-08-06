package com.envisionad.webservice.payment.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * A business may hold only one live subscription per bundle. Mirrors the partial unique
 * index {@code uq_bundle_subscriptions_active_per_business}, but is enforced at the
 * service layer first — the index is Flyway-only and does not exist in the
 * entity-generated test schema.
 *
 * <p>Note this fires only for ACTIVE/PAST_DUE. An INCOMPLETE row is an abandoned
 * checkout, which the subscribe path reuses rather than rejecting.
 */
@ResponseStatus(code = HttpStatus.CONFLICT, reason = "Business already subscribes to this bundle")
public class DuplicateBundleSubscriptionException extends RuntimeException {
    public DuplicateBundleSubscriptionException(String bundleId, String businessId) {
        super("Business " + businessId + " already has a live subscription to bundle " + bundleId + ".");
    }
}
