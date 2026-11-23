package com.example.transporterlanes.exception;

/** An assignment was requested before any input data was submitted. Maps to HTTP 404. */
public class NoInputDataException extends RuntimeException {

    public NoInputDataException() {
        super("No input data found. Submit lanes and transporters to /api/v1/transporters/input first.");
    }
}
