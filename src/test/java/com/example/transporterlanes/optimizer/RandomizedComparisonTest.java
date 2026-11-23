package com.example.transporterlanes.optimizer;

import static com.example.transporterlanes.optimizer.SolutionAssertions.assertValidSolution;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Compares {@link ExactCombinationSolver} with {@link BruteForceSolver} on many small random problems.
 * Quotes are drawn from narrow ranges on purpose so that cost ties, size ties and equal per-lane quotes are common.
 */
class RandomizedComparisonTest {

    private static final long SEED = 20_261_122L;
    private static final int CASES = 1_000;

    private final AssignmentSolver production = new ExactCombinationSolver();
    private final BruteForceSolver reference = new BruteForceSolver();

    @Test
    void productionSolverMatchesBruteForceOnRandomProblems() {
        Random random = new Random(SEED);
        Stats stats = new Stats();

        for (int i = 0; i < CASES; i++) {
            OptimizationProblem problem = randomProblem(random);
            String context = "case " + i + ": " + problem;
            stats.kAboveTransporterCount += problem.maxTransporters() > problem.transporterIds().size() ? 1 : 0;

            List<BruteForceSolver.Candidate> candidates = reference.feasibleCandidates(problem);
            if (candidates.isEmpty()) {
                stats.infeasible++;
                var expected = assertThatExceptionOfType(NoFeasibleAssignmentException.class)
                        .as(context).isThrownBy(() -> reference.solve(problem)).actual();
                assertThatExceptionOfType(NoFeasibleAssignmentException.class).as(context)
                        .isThrownBy(() -> production.solve(problem))
                        .satisfies(e -> {
                            assertThat(e.getLanesWithoutQuotes()).as(context)
                                    .isEqualTo(expected.getLanesWithoutQuotes());
                            assertThat(e.getMaxTransporters()).as(context).isEqualTo(expected.getMaxTransporters());
                        });
                continue;
            }

            AssignmentSolution expected = reference.solve(problem);
            AssignmentSolution actual = production.solve(problem);

            assertThat(actual).as(context).isEqualTo(expected); // cost, selected ids and every lane assignment
            assertValidSolution(problem, actual);
            stats.record(candidates, actual, problem);
        }

        System.out.println(stats.summary());
        // Guard against a generator that silently stops exercising the interesting paths.
        assertThat(stats.infeasible).isGreaterThan(50);
        assertThat(stats.costTies).isGreaterThan(100);
        assertThat(stats.decidedByLexicographicOrder).isGreaterThan(20);
        assertThat(stats.laneQuoteTies).isGreaterThan(20);
        assertThat(stats.kAboveTransporterCount).isGreaterThan(50);
    }

    /**
     * Same comparison with quotes near Long.MAX_VALUE / 3, so three of them overflow a long: exercises skipping of
     * overflowed combinations, exact recounting in their extensions, and the all-overflow failure.
     */
    @Test
    void productionSolverMatchesBruteForceWhenTotalsOverflow() {
        Random random = new Random(SEED + 1);
        int solved = 0;
        int allOverflow = 0;
        int infeasible = 0;
        for (int i = 0; i < 500; i++) {
            OptimizationProblem problem = randomOverflowProblem(random);
            String context = "overflow case " + i + ": " + problem;
            if (reference.feasibleCandidates(problem).isEmpty()) {
                infeasible++;
                assertThatExceptionOfType(NoFeasibleAssignmentException.class).as(context)
                        .isThrownBy(() -> production.solve(problem));
                continue;
            }
            AssignmentSolution expected;
            try {
                expected = reference.solve(problem);
            } catch (ArithmeticException everyTotalOverflows) {
                allOverflow++;
                assertThatExceptionOfType(ArithmeticException.class).as(context)
                        .isThrownBy(() -> production.solve(problem));
                continue;
            }
            AssignmentSolution actual = production.solve(problem);
            assertThat(actual).as(context).isEqualTo(expected);
            assertValidSolution(problem, actual);
            solved++;
        }
        System.out.printf("Overflow comparison (seed %d, 500 cases): solved %d, all-overflow %d, infeasible %d%n",
                SEED + 1, solved, allOverflow, infeasible);
        assertThat(solved).isGreaterThan(100);
        assertThat(allOverflow).isGreaterThan(10);
    }

    private static OptimizationProblem randomOverflowProblem(Random random) {
        int laneCount = 1 + random.nextInt(5);
        int transporterCount = 1 + random.nextInt(6);
        int k = 1 + random.nextInt(transporterCount + 1);
        List<Long> laneIds = distinctShuffledIds(random, laneCount);
        List<Long> transporterIds = distinctShuffledIds(random, transporterCount);
        List<Quote> quotes = new ArrayList<>();
        for (long lane : laneIds) {
            for (long transporter : transporterIds) {
                if (random.nextDouble() < 0.25) {
                    continue; // missing quote
                }
                long amount = random.nextBoolean()
                        ? Long.MAX_VALUE / 3 + 1 + random.nextInt(1_000) // two fit in a long, three never do
                        : 1 + random.nextInt(5);
                quotes.add(new Quote(lane, transporter, amount));
            }
        }
        return new OptimizationProblem(laneIds, transporterIds, quotes, k);
    }

    private static OptimizationProblem randomProblem(Random random) {
        int laneCount = 1 + random.nextInt(6);         // 1..6
        int transporterCount = 1 + random.nextInt(8);  // 1..8
        double missingRate = new double[] {0.0, 0.2, 0.4, 0.6}[random.nextInt(4)];
        int maxQuote = 1 + random.nextInt(12);         // narrow ranges force ties
        int k = 1 + random.nextInt(transporterCount + 2); // sometimes above the transporter count

        List<Long> laneIds = distinctShuffledIds(random, laneCount);
        List<Long> transporterIds = distinctShuffledIds(random, transporterCount);
        List<Quote> quotes = new ArrayList<>();
        for (long lane : laneIds) {
            for (long transporter : transporterIds) {
                if (random.nextDouble() >= missingRate) {
                    quotes.add(new Quote(lane, transporter, 1 + random.nextInt(maxQuote)));
                }
            }
        }
        return new OptimizationProblem(laneIds, transporterIds, quotes, k);
    }

    /** Non-contiguous ids in random input order, so sorting by id is actually exercised. */
    private static List<Long> distinctShuffledIds(Random random, int count) {
        List<Long> pool = new ArrayList<>();
        for (long id = 1; id <= 40; id++) {
            pool.add(id);
        }
        Collections.shuffle(pool, random);
        return new ArrayList<>(pool.subList(0, count));
    }

    private static final class Stats {
        int feasible;
        int infeasible;
        int costTies;                    // several feasible subsets share the optimal cost
        int decidedByLexicographicOrder; // ... and several of those also share the smallest size
        int laneQuoteTies;               // winning subset has two members quoting the same lowest price on a lane
        int kAboveTransporterCount;

        void record(List<BruteForceSolver.Candidate> candidates, AssignmentSolution winner, OptimizationProblem p) {
            feasible++;
            List<BruteForceSolver.Candidate> cheapest = candidates.stream()
                    .filter(c -> c.cost().equals(BigInteger.valueOf(winner.totalCost()))).toList();
            if (cheapest.size() > 1) {
                costTies++;
                long smallestSize = cheapest.stream().filter(c -> c.members().size() == winner.selectedTransporterIds().size()).count();
                if (smallestSize > 1) {
                    decidedByLexicographicOrder++;
                }
            }
            boolean laneTie = winner.assignments().stream().anyMatch(a -> p.quotes().stream()
                    .filter(q -> q.laneId() == a.laneId() && q.amount() == a.quote()
                            && winner.selectedTransporterIds().contains(q.transporterId()))
                    .count() > 1);
            laneQuoteTies += laneTie ? 1 : 0;
        }

        String summary() {
            return """
                    Randomized comparison (seed %d, %d cases):
                      feasible: %d, infeasible: %d, K above transporter count: %d
                      optimal-cost ties: %d (of which decided by lexicographic order: %d)
                      winners with an equal-quote tie on some lane: %d""".formatted(
                    SEED, CASES, feasible, infeasible, kAboveTransporterCount,
                    costTies, decidedByLexicographicOrder, laneQuoteTies);
        }
    }
}
