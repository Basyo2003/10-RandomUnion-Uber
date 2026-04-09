package com.team10.uber.ride.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class RideNotFoundException extends RuntimeException {
    
    public RideNotFoundException(String message) {
        super(message);
    }

    public RideNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
