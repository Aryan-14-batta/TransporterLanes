package com.example.transporterlanes.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "A trade lane between two locations.")
public record LaneRequest(
        @Schema(description = "Client-assigned lane id, unique within the request.", example = "1", minimum = "1")
        @NotNull @Positive Long id,
        @Schema(description = "Origin location; non-blank, at most 100 characters.", example = "Mumbai")
        @NotBlank @Size(max = 100) String origin,
        @Schema(description = "Destination location; non-blank, at most 100 characters.", example = "Delhi")
        @NotBlank @Size(max = 100) String destination) {
}
