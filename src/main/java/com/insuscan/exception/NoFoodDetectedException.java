package com.insuscan.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a food image is valid but no food items were detected. 422 Unprocessable Entity - image was valid but no food found
 */
 
@ResponseStatus(code = HttpStatus.UNPROCESSABLE_ENTITY)
public class NoFoodDetectedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public NoFoodDetectedException() {
        super("No food detected in image");
    }

    public NoFoodDetectedException(String message) {
        super(message);
    }

    public NoFoodDetectedException(Exception cause) {
        super(cause);
    }

    public NoFoodDetectedException(String message, Exception cause) {
        super(message, cause);
    }
}