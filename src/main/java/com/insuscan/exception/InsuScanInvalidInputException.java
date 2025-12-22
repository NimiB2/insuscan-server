package com.insuscan.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// 400 Bad Request
@ResponseStatus(code = HttpStatus.BAD_REQUEST)
public class InsuScanInvalidInputException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public InsuScanInvalidInputException() {
        super();
    }

    public InsuScanInvalidInputException(String message) {
        super(message);
    }

    public InsuScanInvalidInputException(Exception cause) {
        super(cause);
    }

    public InsuScanInvalidInputException(String message, Exception cause) {
        super(message, cause);
    }
}
