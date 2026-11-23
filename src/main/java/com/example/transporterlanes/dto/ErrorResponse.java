package com.example.transporterlanes.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Body of every error response. {@code errors} lists field-level problems and is empty when there are none. */
@Schema(description = "Body of every error response.")
public record ErrorResponse(
        @Schema(example = "error") String status,
        @Schema(description = "What went wrong.") String message,
        @Schema(description = "Field-level problems; empty when the error is not about specific fields.")
        List<FieldError> errors) {

    public record FieldError(
            @Schema(description = "Path of the offending field.", example = "transporters[0].laneQuotes[1].laneId")
            String field,
            @Schema(example = "Unknown lane id 99") String message) {
    }

    public static ErrorResponse of(String message) {
        return new ErrorResponse("error", message, List.of());
    }

    public static ErrorResponse of(String message, List<FieldError> errors) {
        return new ErrorResponse("error", message, List.copyOf(errors));
    }
}
