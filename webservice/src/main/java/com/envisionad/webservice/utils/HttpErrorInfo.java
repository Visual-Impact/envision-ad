package com.envisionad.webservice.utils;

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

    public HttpErrorInfo(HttpStatus httpStatus, String message) {
        this(httpStatus, message, null);
    }

    /**
     * {@code code} is a stable, machine-readable identifier (e.g. "NOT_ADVERTISER") for the
     * rare error a frontend needs to branch on. Most exceptions don't need one — {@code message}
     * stays free-text and is not meant for display, per the frontend's error-handling convention.
     */
    public HttpErrorInfo(HttpStatus httpStatus, String message, String code) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss a");
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("America/Montreal"));

        timestamp = now.format(formatter);
        this.httpStatus = httpStatus;
        this.message = message;
        this.code = code;
    }
}
