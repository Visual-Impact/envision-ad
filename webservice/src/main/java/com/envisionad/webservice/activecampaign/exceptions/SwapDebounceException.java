package com.envisionad.webservice.activecampaign.exceptions;

import lombok.Getter;

/**
 * Thrown when a swap or manual notify is attempted inside the shared cooldown window.
 *
 * <p>Media owners act on these emails by physically changing what is on a screen, so a burst of
 * them is not information, it is noise — and a owner acting on a superseded one leaves the wrong
 * creative up. Both endpoints count against the same clock because they send the same email to
 * the same people.
 *
 * <p>The automatic sweep is deliberately exempt: it is rate-limited far more strictly by its own
 * quiet period, and letting a manual action suppress it would reopen the gap it exists to close.
 */
@Getter
public class SwapDebounceException extends RuntimeException {

    /** Seconds until the caller may retry — surfaced in the error body for the UI countdown. */
    private final int retryAfterSeconds;

    public SwapDebounceException(int retryAfterSeconds) {
        super("Media owners were notified recently. You can notify them again in "
                + retryAfterSeconds + " second(s).");
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
