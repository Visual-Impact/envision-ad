package com.envisionad.webservice.media.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.CONFLICT, reason = "Rejected media cannot be re-activated")
public class MediaRejectedReactivationException extends RuntimeException {
    public MediaRejectedReactivationException() {
        super("Rejected media cannot be re-activated. Please create a new media item instead.");
    }
}
