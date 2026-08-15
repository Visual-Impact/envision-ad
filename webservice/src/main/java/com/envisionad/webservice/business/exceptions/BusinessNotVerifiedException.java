package com.envisionad.webservice.business.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class BusinessNotVerifiedException extends RuntimeException {
    public BusinessNotVerifiedException(String businessId) {
        super("Business " + businessId + " is not verified and cannot subscribe to a bundle.");
    }
}
