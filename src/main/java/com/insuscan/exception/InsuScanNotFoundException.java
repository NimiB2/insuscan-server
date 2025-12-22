package com.insuscan.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// 404 Not Found
@ResponseStatus(code = HttpStatus.NOT_FOUND)
public class InsuScanNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public InsuScanNotFoundException() {
        super();
    }

    public InsuScanNotFoundException(String message) {
        super(message);
    }

    public InsuScanNotFoundException(Exception cause) {
        super(cause);
    }

    public InsuScanNotFoundException(String message, Exception cause) {
        super(message, cause);
    }
}
