package com.envisionad.webservice.media.exceptions;

import com.envisionad.webservice.media.DataAccessLayer.Status;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.CONFLICT, reason = "Invalid media status transition")
public class InvalidMediaStatusTransitionException extends RuntimeException {
    public InvalidMediaStatusTransitionException(Status current, Status target) {
        super("Invalid status transition: " + current + " -> " + target);
    }
}
