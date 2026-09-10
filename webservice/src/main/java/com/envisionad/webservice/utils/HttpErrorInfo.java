package com.envisionad.webservice.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Getter
public class HttpErrorInfo {

    private final String timestamp;
    private final HttpStatus httpStatus;
    private final String message;
    private final String code;

    /**
     * How long the caller must wait before retrying, for the rate-limited endpoints (P6's
     * campaign swap and manual notify share a cooldown). Omitted from the JSON entirely on every
     * other error, so no existing error body changes shape.
     *
     * <p>Carried in the body rather than only as a {@code Retry-After} header because the
     * frontend renders a live countdown from it, and because every handler in this advice
     * returns a bare {@code HttpErrorInfo} — setting a header would mean the one 429 handler
     * returning a {@code ResponseEntity} instead, for no gain the client can use.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final Integer retryAfterSeconds;

    public HttpErrorInfo(HttpStatus httpStatus, String message) {
        this(httpStatus, message, null);
    }

    /**
     * {@code code} is a stable, machine-readable identifier (e.g. "NOT_ADVERTISER") for the
     * rare error a frontend needs to branch on. Most exceptions don't need one — {@code message}
     * stays free-text and is not meant for display, per the frontend's error-handling convention.
     */
    public HttpErrorInfo(HttpStatus httpStatus, String message, String code) {
        this(httpStatus, message, code, null);
    }

    public HttpErrorInfo(HttpStatus httpStatus, String message, String code, Integer retryAfterSeconds) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss a");
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("America/Montreal"));

        timestamp = now.format(formatter);
        this.httpStatus = httpStatus;
        this.message = message;
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
