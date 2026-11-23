package com.example.transporterlanes.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record AssignmentResponse(
        @Schema(example = "success") String status,
        @Schema(description = "Sum of the assigned quotes, in rupees.", example = "29000") long totalCost,
        @Schema(description = "Exactly one entry per lane, ordered by lane id.") List<LaneAssignmentResponse> assignments,
        @Schema(description = "Distinct transporters used, ascending. Each serves at least one lane.")
        List<Long> selectedTransporters) {

    public static AssignmentResponse success(
            long totalCost, List<LaneAssignmentResponse> assignments, List<Long> selectedTransporters) {
        return new AssignmentResponse("success", totalCost, assignments, selectedTransporters);
    }
}
