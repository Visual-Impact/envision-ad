package com.envisionad.webservice.proofofdisplay.presentationlayer.models;

/**
 * Where a subscription stands on its proof-of-display link (P12). Exactly these three; the
 * frontend renders one state per value.
 */
public enum ProofLinkStatus {
    /** A link has been submitted. */
    SET,
    /** Single-owner subscription with no link yet. */
    NOT_SET,
    /** The subscription spans several media owners, which v1 does not support. */
    NOT_APPLICABLE
}
