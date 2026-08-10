package com.envisionad.webservice.advertisement.exceptions;

public class VideoTooLongException extends RuntimeException {
    private static final String MESSAGE = "The uploaded video is %s seconds long, which exceeds the %s second maximum.";
    public VideoTooLongException(double durationInSeconds, int maxSeconds) {
        super(MESSAGE.formatted(durationInSeconds, maxSeconds));
    }
}
