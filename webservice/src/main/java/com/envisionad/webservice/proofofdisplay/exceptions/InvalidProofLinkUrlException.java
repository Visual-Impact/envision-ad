package com.envisionad.webservice.proofofdisplay.exceptions;

/**
 * The submitted proof link is not a well-formed {@code http}/{@code https} URL. The link is later
 * rendered as an {@code <a href>} on the advertiser's dashboard, so rejecting other schemes
 * ({@code javascript:}, {@code data:}) is an XSS control (FR-1.2).
 * <p>
 * Extends {@link IllegalArgumentException}, which {@code GlobalControllerHandler} already maps to
 * 400.
 */
public class InvalidProofLinkUrlException extends IllegalArgumentException {
    public InvalidProofLinkUrlException(String reason) {
        super("Invalid proof link: " + reason);
    }
}
