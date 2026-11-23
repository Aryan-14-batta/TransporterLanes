package com.example.transporterlanes.optimizer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Invariants every returned solution must satisfy, independent of which combination won. */
final class SolutionAssertions {

    private SolutionAssertions() {
    }

    static void assertValidSolution(OptimizationProblem problem, AssignmentSolution solution) {
        Map<Long, Map<Long, Long>> prices = new HashMap<>();
        problem.quotes().forEach(q -> prices.computeIfAbsent(q.laneId(), x -> new HashMap<>())
                .put(q.transporterId(), q.amount()));
        List<Long> selected = solution.selectedTransporterIds();
        int maxSize = Math.min(problem.maxTransporters(), problem.transporterIds().size());

        assertThat(selected).as("selected transporters").isNotEmpty().isSorted().doesNotHaveDuplicates()
                .hasSizeLessThanOrEqualTo(maxSize);
        assertThat(problem.transporterIds()).as("selected transporters exist").containsAll(selected);

        assertThat(solution.assignments()).extracting(LaneAssignment::laneId)
                .as("every lane covered exactly once, ascending")
                .containsExactlyElementsOf(problem.laneIds().stream().sorted().toList());

        long sum = 0;
        Set<Long> used = new HashSet<>();
        for (LaneAssignment assignment : solution.assignments()) {
            Map<Long, Long> laneQuotes = prices.getOrDefault(assignment.laneId(), Map.of());
            assertThat(selected).as("lane %d assigned inside the selected subset", assignment.laneId())
                    .contains(assignment.transporterId());
            assertThat(laneQuotes.get(assignment.transporterId()))
                    .as("lane %d reports the real quote", assignment.laneId()).isEqualTo(assignment.quote());

            long cheapest = selected.stream().filter(laneQuotes::containsKey).mapToLong(laneQuotes::get)
                    .min().orElseThrow();
            long lowestIdAtCheapest = selected.stream()
                    .filter(t -> laneQuotes.containsKey(t) && laneQuotes.get(t) == cheapest).min(Long::compare)
                    .orElseThrow();
            assertThat(assignment.quote()).as("lane %d uses the cheapest quote in the subset", assignment.laneId())
                    .isEqualTo(cheapest);
            assertThat(assignment.transporterId()).as("lane %d equal-quote tie goes to lower id", assignment.laneId())
                    .isEqualTo(lowestIdAtCheapest);

            sum += assignment.quote();
            used.add(assignment.transporterId());
        }
        assertThat(solution.totalCost()).as("total cost equals sum of assigned quotes").isEqualTo(sum);
        assertThat(used).as("every selected transporter serves at least one lane").containsExactlyInAnyOrderElementsOf(selected);
    }
}
