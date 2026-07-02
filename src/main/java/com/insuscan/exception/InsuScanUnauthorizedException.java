package com.insuscan.exception;

import org.springframework.http.HttpStatus;

import org.springframework.web.bind.annotation.ResponseStatus;
/**
 * Thrown when the caller lacks permission to perform the requested action. 401 Unauthorized
 */

@ResponseStatus(code = HttpStatus.UNAUTHORIZED)
public class InsuScanUnauthorizedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public InsuScanUnauthorizedException() {
        super();
    }

    public InsuScanUnauthorizedException(String message) {
        super(message);
    }

    public InsuScanUnauthorizedException(Exception cause) {
        super(cause);
    }

    public InsuScanUnauthorizedException(String message, Exception cause) {
        super(message, cause);
    }
}
