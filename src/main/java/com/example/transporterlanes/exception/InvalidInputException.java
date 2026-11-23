package com.example.transporterlanes.exception;

import com.example.transporterlanes.dto.ErrorResponse.FieldError;
import java.util.List;

/** The request is well-formed but inconsistent (duplicate ids, unknown lane references, ...). Maps to HTTP 400. */
public class InvalidInputException extends RuntimeException {

    private final List<FieldError> errors;

    public InvalidInputException(List<FieldError> errors) {
        super("Input data is invalid.");
        this.errors = List.copyOf(errors);
    }

    public List<FieldError> getErrors() {
        return errors;
    }
}
