package com.example.transporterlanes.optimizer;

import java.util.List;

/**
 * The optimal assignment.
 *
 * @param totalCost              sum of the assigned quotes
 * @param assignments            exactly one entry per lane, ordered by ascending lane id
 * @param selectedTransporterIds the chosen transporter combination, ascending; every one of them serves at least one
 *                               lane (a member serving nothing would make a smaller, equal-cost combination preferable)
 */
public record AssignmentSolution(long totalCost, List<LaneAssignment> assignments, List<Long> selectedTransporterIds) {

    public AssignmentSolution {
        assignments = List.copyOf(assignments);
        selectedTransporterIds = List.copyOf(selectedTransporterIds);
    }
}
