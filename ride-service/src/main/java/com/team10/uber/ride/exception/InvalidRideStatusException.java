package com.team10.uber.ride.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidRideStatusException extends RuntimeException {
    
    public InvalidRideStatusException(String message) {
        super(message);
    }

    public InvalidRideStatusException(String message, Throwable cause) {
        super(message, cause);
    }
}
