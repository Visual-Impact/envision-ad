package com.envisionad.webservice.proofofdisplay.exceptions;

/**
 * The subscription covers screens from more than one media owner, so there is no single owner to
 * maintain its proof link (P12 v1 scope decision). Registered as a 409 with code
 * {@code PROOF_LINK_MULTI_OWNER}. Only thrown after the caller is confirmed to own part of the
 * subscription, so an outsider never learns how it is split (P12-PROGRESS D2).
 */
public class ProofLinkMultiOwnerException extends RuntimeException {
    public ProofLinkMultiOwnerException(String subscriptionId) {
        super("Subscription " + subscriptionId + " spans several media owners and cannot carry a proof link");
    }
}
