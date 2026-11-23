package com.example.transporterlanes.dto;

import io.swagger.v3.oas.annotations.media.Schema;
public record LaneAssignmentResponse(
        @Schema(example = "1") long laneId,
        @Schema(example = "1") long transporterId) {
}
