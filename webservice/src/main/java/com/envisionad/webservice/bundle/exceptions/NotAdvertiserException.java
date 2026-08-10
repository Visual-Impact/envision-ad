package com.envisionad.webservice.bundle.exceptions;

public class NotAdvertiserException extends RuntimeException {
    public NotAdvertiserException(String businessId) {
        super("Business " + businessId + " is not registered as an advertiser.");
    }
}
