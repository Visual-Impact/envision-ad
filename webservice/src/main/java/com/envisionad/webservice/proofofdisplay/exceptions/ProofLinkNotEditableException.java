package com.envisionad.webservice.proofofdisplay.exceptions;

import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionStatus;

/**
 * The subscription is not running, so its link cannot be changed: {@code CANCELED} freezes the
 * last saved link (FR-1.4), and {@code INCOMPLETE} never activated, so there is nothing to prove
 * (FR-1.5). Registered as a 409 with code {@code PROOF_LINK_NOT_EDITABLE}.
 */
public class ProofLinkNotEditableException extends RuntimeException {
    public ProofLinkNotEditableException(String subscriptionId, BundleSubscriptionStatus status) {
        super("Proof link for subscription " + subscriptionId + " cannot be edited while it is " + status);
    }
}
