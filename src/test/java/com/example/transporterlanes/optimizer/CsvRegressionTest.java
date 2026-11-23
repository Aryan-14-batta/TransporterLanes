package com.example.transporterlanes.optimizer;

import static com.example.transporterlanes.optimizer.SolutionAssertions.assertValidSolution;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Regression against the assignment's dataset (9 lanes x 7 transporters). Expected values were computed
 * independently with a throwaway Python script, not with this code base.
 */
class CsvRegressionTest {

    private final AssignmentSolver solver = new ExactCombinationSolver();

    static Stream<Arguments> expectedResults() {
        // assignedTransporterByLane[i] = transporter assigned to lane i+1
        return Stream.of(
                Arguments.of(1, 201_259L, List.of(1L), new long[] {1, 1, 1, 1, 1, 1, 1, 1, 1}),
                Arguments.of(2, 148_402L, List.of(1L, 4L), new long[] {4, 1, 4, 4, 4, 1, 1, 1, 1}),
                Arguments.of(3, 134_876L, List.of(1L, 4L, 6L), new long[] {4, 1, 4, 4, 4, 1, 1, 6, 6}),
                Arguments.of(4, 127_844L, List.of(1L, 3L, 4L, 7L), new long[] {4, 1, 4, 4, 3, 1, 1, 3, 7}),
                Arguments.of(5, 125_045L, List.of(1L, 3L, 4L, 5L, 7L), new long[] {5, 1, 4, 4, 3, 1, 1, 3, 7}),
                Arguments.of(6, 125_045L, List.of(1L, 3L, 4L, 5L, 7L), new long[] {5, 1, 4, 4, 3, 1, 1, 3, 7}),
                Arguments.of(7, 125_045L, List.of(1L, 3L, 4L, 5L, 7L), new long[] {5, 1, 4, 4, 3, 1, 1, 3, 7}));
    }

    @ParameterizedTest(name = "K={0}: cost {1}, transporters {2}")
    @MethodSource("expectedResults")
    void matchesExpectedOptimum(int k, long expectedCost, List<Long> expectedSelected, long[] expectedByLane) {
        OptimizationProblem problem = CsvProblems.load(CsvProblems.ASSIGNMENT_DATASET, k);
        Map<String, Long> csvQuote = problem.quotes().stream()
                .collect(Collectors.toMap(q -> q.laneId() + ":" + q.transporterId(), Quote::amount));

        AssignmentSolution solution = solver.solve(problem);

        assertThat(solution.totalCost()).isEqualTo(expectedCost);
        assertThat(solution.selectedTransporterIds()).containsExactlyElementsOf(expectedSelected);
        assertThat(solution.assignments()).extracting(LaneAssignment::transporterId)
                .as("transporter per lane 1..9")
                .containsExactlyElementsOf(Arrays.stream(expectedByLane).boxed().toList());
        for (LaneAssignment assignment : solution.assignments()) {
            assertThat(assignment.quote()).as("lane %d quote comes from the CSV", assignment.laneId())
                    .isEqualTo(csvQuote.get(assignment.laneId() + ":" + assignment.transporterId()));
        }
        assertThat(solution.assignments().stream().mapToLong(LaneAssignment::quote).sum()).isEqualTo(expectedCost);
        // Coverage exactly once, membership, cheapest-in-subset and sum = total:
        assertValidSolution(problem, solution);
    }
}
