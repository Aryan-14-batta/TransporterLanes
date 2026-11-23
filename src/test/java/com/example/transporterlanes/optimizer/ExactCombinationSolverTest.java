package com.example.transporterlanes.optimizer;

import static com.example.transporterlanes.optimizer.SolutionAssertions.assertValidSolution;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.example.transporterlanes.optimizer.ProblemTooLargeException.Limit;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ExactCombinationSolverTest {

    private final AssignmentSolver solver = new ExactCombinationSolver();

    private AssignmentSolution solveAndValidate(OptimizationProblem problem) {
        AssignmentSolution solution = solver.solve(problem);
        assertValidSolution(problem, solution);
        return solution;
    }

    @Nested
    class BasicBehaviour {

        @Test
        void oneLaneOneTransporter() {
            var problem = ProblemBuilder.lanes(1).transporter(7, 500).build(1);

            AssignmentSolution solution = solveAndValidate(problem);

            assertThat(solution.totalCost()).isEqualTo(500);
            assertThat(solution.selectedTransporterIds()).containsExactly(7L);
            assertThat(solution.assignments()).containsExactly(new LaneAssignment(1, 7, 500));
        }

        @Test
        void multipleLanesAndTransportersPickCheapestPerLaneWithinTheSubset() {
            var problem = ProblemBuilder.lanes(1, 2, 3)
                    .transporter(1, 10, 50, 50)
                    .transporter(2, 50, 10, 50)
                    .transporter(3, 50, 50, 10)
                    .build(3);

            AssignmentSolution solution = solveAndValidate(problem);

            assertThat(solution.totalCost()).isEqualTo(30);
            assertThat(solution.selectedTransporterIds()).containsExactly(1L, 2L, 3L);
            assertThat(solution.assignments()).containsExactly(
                    new LaneAssignment(1, 1, 10), new LaneAssignment(2, 2, 10), new LaneAssignment(3, 3, 10));
        }

        @Test
        void maxTransportersIsAnUpperLimitNotAnExactCount() {
            // One transporter: 30,000 total. The best two-transporter split without T1 costs 35,000.
            var problem = ProblemBuilder.lanes(1, 2)
                    .transporter(1, 15_000, 15_000)
                    .transporter(2, 17_000, null)
                    .transporter(3, null, 18_000)
                    .build(2);

            AssignmentSolution solution = solveAndValidate(problem);

            assertThat(solution.totalCost()).isEqualTo(30_000);
            assertThat(solution.selectedTransporterIds()).containsExactly(1L);
        }

        @Test
        void missingQuotesAreIgnored() {
            // T2 is cheapest on lane 1 but has no quote for lane 2; lane 2 must go to T1.
            var problem = ProblemBuilder.lanes(1, 2)
                    .transporter(1, 100, 100)
                    .transporter(2, 10, null)
                    .build(2);

            AssignmentSolution solution = solveAndValidate(problem);

            assertThat(solution.totalCost()).isEqualTo(110);
            assertThat(solution.assignments()).containsExactly(
                    new LaneAssignment(1, 2, 10), new LaneAssignment(2, 1, 100));
        }

        @Test
        void subsetThatCannotCoverAllLanesIsRejectedEvenIfCheaper() {
            // With K = 1, T2 alone would be cheaper but cannot serve lane 2.
            var problem = ProblemBuilder.lanes(1, 2)
                    .transporter(1, 100, 100)
                    .transporter(2, 1, null)
                    .build(1);

            AssignmentSolution solution = solveAndValidate(problem);

            assertThat(solution.selectedTransporterIds()).containsExactly(1L);
            assertThat(solution.totalCost()).isEqualTo(200);
        }

        @Test
        void kEqualToOneUsesTheSingleCheapestFullCoverageTransporter() {
            var problem = ProblemBuilder.lanes(1, 2)
                    .transporter(1, 30, 30)
                    .transporter(2, 25, 30)
                    .transporter(3, 10, 99)
                    .build(1);

            AssignmentSolution solution = solveAndValidate(problem);

            assertThat(solution.selectedTransporterIds()).containsExactly(2L);
            assertThat(solution.totalCost()).isEqualTo(55);
        }

        @Test
        void kGreaterThanTransporterCountIsCappedToTheTransporterCount() {
            var base = ProblemBuilder.lanes(1, 2).transporter(1, 5, 50).transporter(2, 50, 5);

            AssignmentSolution capped = solveAndValidate(base.build(2));
            AssignmentSolution oversized = solveAndValidate(base.build(100));

            assertThat(oversized).isEqualTo(capped);
            assertThat(oversized.selectedTransporterIds()).containsExactly(1L, 2L);
            assertThat(oversized.totalCost()).isEqualTo(10);
        }

        @Test
        void transporterIdsNeedNotBeContiguousOrSorted() {
            var problem = ProblemBuilder.lanes(30, 10, 20)
                    .transporter(900, 5, 50, 50)
                    .transporter(42, 50, 5, 5)
                    .build(2);

            AssignmentSolution solution = solveAndValidate(problem);

            assertThat(solution.selectedTransporterIds()).containsExactly(42L, 900L);
            assertThat(solution.assignments()).extracting(LaneAssignment::laneId).containsExactly(10L, 20L, 30L);
        }

        @Test
        void solverRejectsNonPositiveLimits() {
            assertThatIllegalArgumentException().isThrownBy(() -> new ExactCombinationSolver(0, 1))
                    .withMessageContaining("maxLaneEvaluations must be positive");
            assertThatIllegalArgumentException().isThrownBy(() -> new ExactCombinationSolver(1, 0))
                    .withMessageContaining("maxMatrixCells must be positive");
            assertThatIllegalArgumentException().isThrownBy(() -> new ExactCombinationSolver(-5, -5));
        }
    }

    @Nested
    class Infeasibility {

        @Test
        void laneWithNoQuotesAtAllIsReportedByLaneId() {
            var problem = ProblemBuilder.lanes(1, 2, 3)
                    .transporter(1, 10, null, 10)
                    .transporter(2, 10, null, null)
                    .build(2);

            assertThatExceptionOfType(NoFeasibleAssignmentException.class)
                    .isThrownBy(() -> solver.solve(problem))
                    .withMessageContaining("lane(s) [2]")
                    .satisfies(e -> assertThat(e.getLanesWithoutQuotes()).containsExactly(2L));
        }

        @Test
        void noCombinationWithinKCoversAllLanes() {
            // Every lane is quoted by someone, but covering all three needs all three transporters.
            var builder = ProblemBuilder.lanes(1, 2, 3)
                    .transporter(1, 10, null, null)
                    .transporter(2, null, 10, null)
                    .transporter(3, null, null, 10);

            assertThatExceptionOfType(NoFeasibleAssignmentException.class)
                    .isThrownBy(() -> solver.solve(builder.build(2)))
                    .withMessage("No combination of at most 2 transporter(s) covers all 3 lanes.")
                    .satisfies(e -> {
                        assertThat(e.getMaxTransporters()).isEqualTo(2);
                        assertThat(e.getLanesWithoutQuotes()).isEmpty();
                    });

            assertThat(solveAndValidate(builder.build(3)).totalCost()).isEqualTo(30);
        }
    }

    @Nested
    class TieBreaking {

        @Test
        void equalCostPrefersFewerTransportersEvenOverALexicographicallySmallerList() {
            // {2} costs 20. {1,2} also costs 20 and [1,2] < [2] lexicographically, but fewer transporters wins.
            var problem = ProblemBuilder.lanes(1, 2)
                    .transporter(1, 10, null)
                    .transporter(2, 10, 10)
                    .build(2);

            AssignmentSolution solution = solveAndValidate(problem);

            assertThat(solution.selectedTransporterIds()).containsExactly(2L);
            assertThat(solution.totalCost()).isEqualTo(20);
        }

        @Test
        void equalCostAndSizePrefersLexicographicallySmallerIds() {
            // Worked example from the design: {1,3} and {2,3} both cost 11 at K = 2.
            var builder = ProblemBuilder.lanes(1, 2, 3)
                    .transporter(1, 4, 9, null)
                    .transporter(2, 6, 3, 8)
                    .transporter(3, null, 5, 2);

            AssignmentSolution k1 = solveAndValidate(builder.build(1));
            AssignmentSolution k2 = solveAndValidate(builder.build(2));
            AssignmentSolution k3 = solveAndValidate(builder.build(3));

            assertThat(k1.selectedTransporterIds()).containsExactly(2L);
            assertThat(k1.totalCost()).isEqualTo(17);
            assertThat(k2.selectedTransporterIds()).containsExactly(1L, 3L);
            assertThat(k2.totalCost()).isEqualTo(11);
            assertThat(k2.assignments()).containsExactly(
                    new LaneAssignment(1, 1, 4), new LaneAssignment(2, 3, 5), new LaneAssignment(3, 3, 2));
            assertThat(k3.selectedTransporterIds()).containsExactly(1L, 2L, 3L);
            assertThat(k3.totalCost()).isEqualTo(9);
        }

        @Test
        void equalQuoteOnALaneGoesToTheLowerTransporterId() {
            // Both are needed (each is uniquely cheap on one lane) and they tie at 7 on lane 1.
            // Transporter 5 is listed first to prove the rule uses ids, not input order.
            var problem = ProblemBuilder.lanes(1, 2, 3)
                    .transporter(5, 7, 1, 9)
                    .transporter(2, 7, 9, 1)
                    .build(2);

            AssignmentSolution solution = solveAndValidate(problem);

            assertThat(solution.selectedTransporterIds()).containsExactly(2L, 5L);
            assertThat(solution.assignments()).containsExactly(
                    new LaneAssignment(1, 2, 7), new LaneAssignment(2, 5, 1), new LaneAssignment(3, 2, 1));
        }
    }

    @Nested
    class Safeguards {

        @Test
        void refusesSearchesAboveTheWorkLimit() {
            // 10 transporters, K = 10: 1023 combinations x 2 lanes = 2046 evaluations > 1000.
            var builder = ProblemBuilder.lanes(1, 2);
            for (long t = 1; t <= 10; t++) {
                builder.transporter(t, 5, 5);
            }
            var problem = builder.build(10);

            assertThatExceptionOfType(ProblemTooLargeException.class)
                    .isThrownBy(() -> new ExactCombinationSolver(1_000, ExactCombinationSolver.DEFAULT_MAX_MATRIX_CELLS)
                            .solve(problem))
                    .withMessageContaining("2046 lane evaluations needed")
                    .satisfies(e -> {
                        assertThat(e.getLimit()).isEqualTo(Limit.LANE_EVALUATIONS);
                        assertThat(e.getRequired()).isEqualTo(2046);
                        assertThat(e.getMaximum()).isEqualTo(1_000);
                    });
        }

        @Test
        void smallKKeepsTheSearchSmallEvenWithManyTransporters() {
            // 200 transporters but K = 2: 200 + 19,900 combinations, well under the default limit.
            var builder = ProblemBuilder.lanes(1, 2);
            for (long t = 1; t <= 200; t++) {
                builder.transporter(t, (int) (1000 - t), (int) (500 + t));
            }

            AssignmentSolution solution = solveAndValidate(builder.build(2));

            assertThat(solution.selectedTransporterIds()).containsExactly(1L, 200L);
            assertThat(solution.totalCost()).isEqualTo(800 + 501);
        }

        @Test
        void combinationCountMatchesBinomialSums() {
            assertThat(ExactCombinationSolver.countCombinationsUpTo(7, 3)).isEqualTo(7 + 21 + 35);
            assertThat(ExactCombinationSolver.countCombinationsUpTo(7, 7)).isEqualTo(127);
            assertThat(ExactCombinationSolver.countCombinationsUpTo(60, 60)).isEqualTo((1L << 60) - 1);
        }

        @Test
        void combinationCountSaturatesInsteadOfOverflowing() {
            assertThat(ExactCombinationSolver.countCombinationsUpTo(200, 200)).isEqualTo(Long.MAX_VALUE);
        }

        @Test
        void saturatedWorkEstimateIsReportedAsMoreThanTheRepresentableLimit() {
            // 200 transporters, K = 200: 2^200 - 1 combinations does not fit in a long.
            var builder = ProblemBuilder.lanes(1);
            for (long t = 1; t <= 200; t++) {
                builder.transporter(t, 5);
            }

            assertThatExceptionOfType(ProblemTooLargeException.class)
                    .isThrownBy(() -> solver.solve(builder.build(200)))
                    .withMessageContaining("more than " + Long.MAX_VALUE)
                    .satisfies(e -> assertThat(e.getRequired()).isEqualTo(Long.MAX_VALUE));
        }
    }

    @Nested
    class SizeLimits {

        // CSV dataset at K = 7: 127 combinations x 9 lanes = 1143 lane evaluations; 9 x 7 = 63 matrix cells.
        private final OptimizationProblem csvAtK7 = CsvProblems.load(CsvProblems.ASSIGNMENT_DATASET, 7);

        @Test
        void problemExactlyAtBothLimitsIsSolved() {
            AssignmentSolution solution = new ExactCombinationSolver(1_143, 63).solve(csvAtK7);

            assertValidSolution(csvAtK7, solution);
            assertThat(solution.totalCost()).isEqualTo(125_045);
        }

        @Test
        void oneEvaluationOverTheWorkLimitIsRefused() {
            assertThatExceptionOfType(ProblemTooLargeException.class)
                    .isThrownBy(() -> new ExactCombinationSolver(1_142, 63).solve(csvAtK7))
                    .satisfies(e -> {
                        assertThat(e.getLimit()).isEqualTo(Limit.LANE_EVALUATIONS);
                        assertThat(e.getRequired()).isEqualTo(1_143);
                    });
        }

        @Test
        void oneCellOverTheMatrixLimitIsRefused() {
            assertThatExceptionOfType(ProblemTooLargeException.class)
                    .isThrownBy(() -> new ExactCombinationSolver(1_143, 62).solve(csvAtK7))
                    .withMessage("Problem too large: 9 lane(s) x 7 transporter(s) = 63 price-table cells, limit is 62."
                            + " Reduce the number of lanes or transporters.")
                    .satisfies(e -> {
                        assertThat(e.getLimit()).isEqualTo(Limit.MATRIX_CELLS);
                        assertThat(e.getRequired()).isEqualTo(63);
                        assertThat(e.getMaximum()).isEqualTo(62);
                    });
        }

        @Test
        void workLimitIsCheckedBeforeTheMatrixLimit() {
            assertThatExceptionOfType(ProblemTooLargeException.class)
                    .isThrownBy(() -> new ExactCombinationSolver(1, 1).solve(csvAtK7))
                    .satisfies(e -> assertThat(e.getLimit()).isEqualTo(Limit.LANE_EVALUATIONS));
        }

        @Test
        void lanesWithoutQuotesAreReportedBeforeAnySizeLimit() {
            var problem = ProblemBuilder.lanes(1, 2, 3)
                    .transporter(1, 10, null, null)
                    .transporter(2, 10, null, null)
                    .build(2);

            assertThatExceptionOfType(NoFeasibleAssignmentException.class)
                    .isThrownBy(() -> new ExactCombinationSolver(1, 1).solve(problem))
                    .satisfies(e -> assertThat(e.getLanesWithoutQuotes()).containsExactly(2L, 3L));
        }

        @Test
        void workLimitFailsBeforeThePriceTableIsAllocated() {
            // 2,000 lanes x 2,000 transporters: a price table would be 4,000,000 longs (32 MB).
            // K = 1 needs 2,000 x 2,000 = 4,000,000 lane evaluations, over the 1,000,000 limit.
            OptimizationProblem problem = sparseProblem();
            var limited = new ExactCombinationSolver(1_000_000, ExactCombinationSolver.DEFAULT_MAX_MATRIX_CELLS);

            ProblemTooLargeException failure = refusedWhileMeasuringAllocation(limited, problem);

            assertThat(failure.getLimit()).isEqualTo(Limit.LANE_EVALUATIONS);
        }

        @Test
        void matrixLimitFailsBeforeThePriceTableIsAllocated() {
            // Same 32 MB table; the work (4,000,000 evaluations) is within the default limit, the matrix is not.
            OptimizationProblem problem = sparseProblem();
            var limited = new ExactCombinationSolver(ExactCombinationSolver.DEFAULT_MAX_LANE_EVALUATIONS, 1_000_000);

            ProblemTooLargeException failure = refusedWhileMeasuringAllocation(limited, problem);

            assertThat(failure.getLimit()).isEqualTo(Limit.MATRIX_CELLS);
            assertThat(failure.getRequired()).isEqualTo(4_000_000);
        }

        @Test
        void defaultLimitsStillSolveTheSameSparseProblem() {
            // Proves the two tests above fail because of the limits, not because the problem is unsolvable.
            OptimizationProblem problem = sparseProblem();

            AssignmentSolution solution = solveAndValidate(problem);

            assertThat(solution.selectedTransporterIds()).containsExactly(1L);
            assertThat(solution.totalCost()).isEqualTo(2_000 * 5);
        }

        /**
         * 2,000 lanes and 2,000 transporters with only 3,999 quotes, K = 1. Transporter 1 quotes every lane at 5;
         * transporter i (i >= 2) quotes only lane i, at 1. Only transporter 1 can cover everything alone.
         */
        private static OptimizationProblem sparseProblem() {
            List<Long> ids = LongStream.rangeClosed(1, 2_000).boxed().toList();
            List<Quote> quotes = new ArrayList<>();
            for (long id : ids) {
                quotes.add(new Quote(id, 1, 5));
                if (id >= 2) {
                    quotes.add(new Quote(id, id, 1));
                }
            }
            return new OptimizationProblem(ids, ids, quotes, 1);
        }

        /**
         * Runs the solver expecting a refusal, and asserts that far less than the 32 MB price table was allocated on
         * this thread while doing so.
         */
        private static ProblemTooLargeException refusedWhileMeasuringAllocation(
                ExactCombinationSolver limited, OptimizationProblem problem) {
            var threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
            assumeTrue(threads.isThreadAllocatedMemorySupported() && threads.isThreadAllocatedMemoryEnabled(),
                    "per-thread allocation measurement not available on this JVM");

            long before = threads.getCurrentThreadAllocatedBytes();
            ProblemTooLargeException failure = null;
            try {
                limited.solve(problem);
            } catch (ProblemTooLargeException e) {
                failure = e;
            }
            long allocated = threads.getCurrentThreadAllocatedBytes() - before;

            assertThat(failure).as("solve must be refused").isNotNull();
            assertThat(allocated).as("bytes allocated before refusing").isLessThan(4_000_000L);
            return failure;
        }
    }

    @Nested
    class Overflow {

        private static final long HUGE = Long.MAX_VALUE / 2 + 1; // two of these overflow a long

        @Test
        void overflowInALosingCombinationDoesNotHideTheCheapSolution() {
            // {T1} alone would total 2 * HUGE (overflow). {T2} costs 2 and must win.
            var problem = new OptimizationProblem(List.of(1L, 2L), List.of(1L, 2L), List.of(
                    new Quote(1, 1, HUGE), new Quote(2, 1, HUGE), new Quote(1, 2, 1), new Quote(2, 2, 1)), 2);

            AssignmentSolution solution = solveAndValidate(problem);

            assertThat(solution.selectedTransporterIds()).containsExactly(2L);
            assertThat(solution.totalCost()).isEqualTo(2);
            assertThat(solution.assignments()).containsExactly(new LaneAssignment(1, 2, 1), new LaneAssignment(2, 2, 1));
        }

        @Test
        void extensionOfAnOverflowedCombinationRecomputesItsExactTotal() {
            // {T1} overflows; {T1,T2} = 1 + 1 + 3 = 5 is the only feasible representable answer once T2 joins.
            var withHugeQuotes = new OptimizationProblem(List.of(1L, 2L, 3L), List.of(1L, 2L), List.of(
                    new Quote(1, 1, HUGE), new Quote(2, 1, HUGE), new Quote(3, 1, 3),
                    new Quote(1, 2, 1), new Quote(2, 2, 1)), 2);

            AssignmentSolution solution = solveAndValidate(withHugeQuotes);

            assertThat(solution.selectedTransporterIds()).containsExactly(1L, 2L);
            assertThat(solution.totalCost()).isEqualTo(5);
        }

        @Test
        void temporaryOverflowWhileUpdatingIncrementallyIsRecountedExactly() {
            // {T1} covers lanes 2 and 3 for (MAX - 5) + 1. Adding T2 first adds lane 1 (+10, which would exceed
            // Long.MAX_VALUE) and only then lowers lane 2 to 1. The true total of {T1,T2} is 10 + 1 + 1 = 12.
            var problem = new OptimizationProblem(List.of(1L, 2L, 3L), List.of(1L, 2L), List.of(
                    new Quote(2, 1, Long.MAX_VALUE - 5), new Quote(3, 1, 1),
                    new Quote(1, 2, 10), new Quote(2, 2, 1)), 2);

            AssignmentSolution solution = solveAndValidate(problem);

            assertThat(solution.selectedTransporterIds()).containsExactly(1L, 2L);
            assertThat(solution.totalCost()).isEqualTo(12);
        }

        @Test
        void totalExactlyAtLongMaxValueIsAccepted() {
            long half = Long.MAX_VALUE / 2;
            var problem = new OptimizationProblem(List.of(1L, 2L), List.of(1L),
                    List.of(new Quote(1, 1, half), new Quote(2, 1, Long.MAX_VALUE - half)), 1);

            assertThat(solveAndValidate(problem).totalCost()).isEqualTo(Long.MAX_VALUE);
        }

        @Test
        void failsClearlyOnlyWhenEveryFeasibleCombinationOverflows() {
            var problem = new OptimizationProblem(List.of(1L, 2L), List.of(1L),
                    List.of(new Quote(1, 1, HUGE), new Quote(2, 1, HUGE)), 1);

            assertThatExceptionOfType(ArithmeticException.class).isThrownBy(() -> solver.solve(problem))
                    .withMessage("Every feasible combination of at most 1 transporter(s) has a total cost above "
                            + Long.MAX_VALUE + ", which cannot be represented.");
        }
    }
}
