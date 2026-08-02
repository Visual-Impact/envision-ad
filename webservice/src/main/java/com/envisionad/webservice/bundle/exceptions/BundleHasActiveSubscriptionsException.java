package com.envisionad.webservice.bundle.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * A bundle that any subscription references cannot be deleted.
 * <p>
 * <strong>Every status blocks it, CANCELED and INCOMPLETE included</strong> (P1 M6, decision
 * D47). Brief req. 5 originally scoped this to live subscriptions, on the stated understanding
 * that "canceled-only history can be deleted along with the bundle (cascade)". That turned out
 * to be false: {@code bundle_subscriptions.bundle_id} carries no {@code ON DELETE} clause, so
 * Postgres defaults to NO ACTION and refuses the delete outright. Rather than add a cascade that
 * would destroy the advertiser's billing history, the guard was widened to match the database.
 */
@ResponseStatus(code = HttpStatus.CONFLICT, reason = "Bundle has subscriptions")
public class BundleHasActiveSubscriptionsException extends RuntimeException {
    public BundleHasActiveSubscriptionsException(String bundleId, long subscriptionCount) {
        super("Bundle " + bundleId + " cannot be deleted: " + subscriptionCount
                + " subscription(s) reference it. Cancelled subscriptions still count — their "
                + "billing history keeps pointing at the bundle.");
    }
}
