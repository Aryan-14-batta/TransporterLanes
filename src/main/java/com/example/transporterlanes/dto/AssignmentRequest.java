package com.example.transporterlanes.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Body of POST /api/v1/transporters/assignment. {@code maxTransporters} is an upper limit, not an exact count. */
public record AssignmentRequest(
        @Schema(description = "Upper limit on the number of distinct transporters used (not an exact count). "
                + "Values above the number of stored transporters are capped.", example = "3")
        @NotNull @Min(1) Integer maxTransporters) {
}
