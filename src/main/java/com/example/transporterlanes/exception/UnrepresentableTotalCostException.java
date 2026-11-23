package com.example.transporterlanes.exception;

/**
 * Every feasible assignment has a total cost too large to represent. Wraps the optimizer's ArithmeticException so
 * that only this specific case, not arbitrary arithmetic errors, maps to HTTP 422.
 */
public class UnrepresentableTotalCostException extends RuntimeException {

    public UnrepresentableTotalCostException(ArithmeticException cause) {
        super(cause.getMessage(), cause);
    }
}
