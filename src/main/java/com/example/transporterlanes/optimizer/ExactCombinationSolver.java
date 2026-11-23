package com.example.transporterlanes.optimizer;

import static com.example.transporterlanes.optimizer.QuoteMatrix.NO_QUOTE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Exact solver that enumerates every transporter combination of size 1..K exactly once.
 *
 * <h2>Algorithm</h2>
 * Combinations are generated depth-first in ascending index order ({0}, {0,1}, {0,1,2}, ..., {0,2}, ..., {1}, ...).
 * Each combination extends its parent (itself minus its highest-id member) by one transporter, so per-lane state is
 * updated incrementally instead of recomputed:
 * <pre>
 *   best[lane] of {parent + t} = min(best[lane] of parent, quote(lane, t))
 * </pre>
 * The running total and the count of still-uncovered lanes are updated in the same pass. A combination is feasible
 * when no lane is uncovered.
 *
 * <h2>Overflow</h2>
 * Per-lane quotes are always exact; only totals can overflow. A combination whose exact total exceeds
 * {@code Long.MAX_VALUE} is more expensive than any representable total, so it is skipped rather than aborting the
 * search; its extensions recompute their totals from the exact per-lane quotes. Only if every feasible combination
 * overflows is an {@link ArithmeticException} thrown.
 *
 * <h2>Complexity</h2>
 * Let n = transporters, L = lanes and C = C(n,1) + C(n,2) + ... + C(n,K) the number of combinations.
 * <ul>
 *   <li>Time: O(L * C), plus O(L * n + Q) to build the price table (Q = number of quotes).
 *       For fixed K, C grows as roughly n^K / K!; for K = n it is 2^n - 1.</li>
 *   <li>Space: O(L * n) for the price table plus O(L * K) for the per-depth search state.</li>
 * </ul>
 * <h2>Safeguards</h2>
 * Both are checked from the input sizes alone, before the price table is allocated or the search starts:
 * <ul>
 *   <li>Work limit: {@code L * C} lane evaluations (default {@value #DEFAULT_MAX_LANE_EVALUATIONS}), bounding time.</li>
 *   <li>Matrix limit: {@code L * n} price-table cells (default {@value #DEFAULT_MAX_MATRIX_CELLS}, about 80 MB of
 *       longs), bounding memory.</li>
 * </ul>
 * Exceeding either throws {@link ProblemTooLargeException}.
 */
public final class ExactCombinationSolver implements AssignmentSolver {

    /** Default work limit, in lane evaluations (one combination x one lane). */
    public static final long DEFAULT_MAX_LANE_EVALUATIONS = 100_000_000L;

    /** Default memory limit, in lanes x transporters cells of the internal price table. */
    public static final long DEFAULT_MAX_MATRIX_CELLS = 10_000_000L;

    private final long maxLaneEvaluations;
    private final long maxMatrixCells;

    public ExactCombinationSolver() {
        this(DEFAULT_MAX_LANE_EVALUATIONS, DEFAULT_MAX_MATRIX_CELLS);
    }

    public ExactCombinationSolver(long maxLaneEvaluations, long maxMatrixCells) {
        if (maxLaneEvaluations < 1) {
            throw new IllegalArgumentException("maxLaneEvaluations must be positive, was " + maxLaneEvaluations);
        }
        if (maxMatrixCells < 1) {
            throw new IllegalArgumentException("maxMatrixCells must be positive, was " + maxMatrixCells);
        }
        this.maxLaneEvaluations = maxLaneEvaluations;
        this.maxMatrixCells = maxMatrixCells;
    }

    @Override
    public AssignmentSolution solve(OptimizationProblem problem) {
        int laneCount = problem.laneIds().size();
        int transporterCount = problem.transporterIds().size();
        int maxSize = Math.min(problem.maxTransporters(), transporterCount);

        // Cheap checks on the input sizes first; nothing proportional to lanes x transporters is allocated yet.
        rejectLanesWithoutQuotes(problem, maxSize);
        rejectOversizedSearch(laneCount, transporterCount, maxSize);
        rejectOversizedMatrix(laneCount, transporterCount);

        QuoteMatrix matrix = QuoteMatrix.from(problem);
        Search search = new Search(matrix, maxSize);
        search.extend(0, 0);
        if (!search.foundFeasible()) {
            if (search.foundOnlyUnrepresentableTotals()) {
                throw new ArithmeticException("Every feasible combination of at most " + maxSize
                        + " transporter(s) has a total cost above " + Long.MAX_VALUE + ", which cannot be represented.");
            }
            throw new NoFeasibleAssignmentException(maxSize, List.of(),
                    "No combination of at most " + maxSize + " transporter(s) covers all "
                            + matrix.laneCount() + " lanes.");
        }
        return search.bestSolution();
    }

    /** Uses only the quote list (O(lanes + quotes)), so it can run before any size check. */
    private static void rejectLanesWithoutQuotes(OptimizationProblem problem, int maxSize) {
        Set<Long> quotedLanes = new HashSet<>();
        for (Quote quote : problem.quotes()) {
            quotedLanes.add(quote.laneId());
        }
        List<Long> uncoverable = problem.laneIds().stream()
                .filter(laneId -> !quotedLanes.contains(laneId))
                .sorted()
                .toList();
        if (!uncoverable.isEmpty()) {
            throw new NoFeasibleAssignmentException(maxSize, uncoverable,
                    "No transporter quotes lane(s) " + uncoverable + ", so they cannot be covered.");
        }
    }

    private void rejectOversizedSearch(int laneCount, int transporterCount, int maxSize) {
        long combinations = countCombinationsUpTo(transporterCount, maxSize);
        long laneEvaluations = saturatingMultiply(combinations, laneCount);
        if (laneEvaluations > maxLaneEvaluations) {
            throw ProblemTooLargeException.searchTooLarge(laneEvaluations, maxLaneEvaluations, maxSize, laneCount);
        }
    }

    private void rejectOversizedMatrix(int laneCount, int transporterCount) {
        // Two ints always fit in a long product; saturating anyway keeps the intent explicit.
        long cells = saturatingMultiply(laneCount, transporterCount);
        if (cells > maxMatrixCells) {
            throw ProblemTooLargeException.matrixTooLarge(laneCount, transporterCount, cells, maxMatrixCells);
        }
    }

    /** C(n,1) + ... + C(n,k), or Long.MAX_VALUE if that does not fit in a long. */
    static long countCombinationsUpTo(int n, int k) {
        long total = 0;
        long current = 1; // C(n, 0)
        for (int size = 1; size <= k; size++) {
            // C(n,size) = C(n,size-1) * (n-size+1) / size; the division is always exact.
            long product = saturatingMultiply(current, n - size + 1);
            if (product == Long.MAX_VALUE) {
                return Long.MAX_VALUE;
            }
            current = product / size;
            if (current > Long.MAX_VALUE - total) {
                return Long.MAX_VALUE;
            }
            total += current;
        }
        return total;
    }

    /** a * b for non-negative operands, or Long.MAX_VALUE on overflow. */
    private static long saturatingMultiply(long a, long b) {
        long high = Math.multiplyHigh(a, b);
        long low = a * b;
        return (high == 0 && low >= 0) ? low : Long.MAX_VALUE;
    }

    /** Mutable depth-first search state. One instance per {@link #solve} call. */
    private static final class Search {

        private final QuoteMatrix matrix;
        private final int maxSize;
        private final int laneCount;

        // Level d describes the current combination of size d; level 0 is the empty combination.
        private final int[] members;          // members[0..d-1] = transporter indices, ascending
        private final long[][] bestQuote;     // [d][lane] cheapest quote within the combination, or NO_QUOTE
        private final int[][] bestOwner;      // [d][lane] transporter index giving that quote
        private final long[] totalCost;       // [d] sum of bestQuote over covered lanes
        private final int[] uncoveredLanes;   // [d] lanes with no quote within the combination
        private final boolean[] overflowed;   // [d] exact total exceeds Long.MAX_VALUE; totalCost[d] is meaningless

        // A combination whose exact total exceeds Long.MAX_VALUE costs more than any representable total, so it can
        // never be optimal unless every feasible combination overflows. Such combinations are skipped, not fatal.
        private boolean sawFeasibleOverflow;

        private boolean found;
        private long bestCost;
        private int[] bestMembers;
        private int[] bestOwners;

        Search(QuoteMatrix matrix, int maxSize) {
            this.matrix = matrix;
            this.maxSize = maxSize;
            this.laneCount = matrix.laneCount();
            this.members = new int[maxSize];
            this.bestQuote = new long[maxSize + 1][laneCount];
            this.bestOwner = new int[maxSize + 1][laneCount];
            this.totalCost = new long[maxSize + 1];
            this.uncoveredLanes = new int[maxSize + 1];
            this.overflowed = new boolean[maxSize + 1];
            uncoveredLanes[0] = laneCount; // bestQuote[0] is all NO_QUOTE thanks to zero-initialisation
        }

        /** Visits every combination that extends the current size-{@code depth} prefix with indices >= {@code from}. */
        void extend(int depth, int from) {
            for (int t = from; t < matrix.transporterCount(); t++) {
                members[depth] = t;
                addTransporter(depth, t);
                considerCurrent(depth + 1);
                if (depth + 1 < maxSize) {
                    extend(depth + 1, t + 1);
                }
            }
        }

        /** Builds level depth+1 from level depth plus transporter t. */
        private void addTransporter(int depth, int t) {
            long[] parentQuote = bestQuote[depth];
            int[] parentOwner = bestOwner[depth];
            long[] childQuote = bestQuote[depth + 1];
            int[] childOwner = bestOwner[depth + 1];
            long cost = totalCost[depth];
            int uncovered = uncoveredLanes[depth];
            // Recount from the exact per-lane quotes if the parent overflowed, or if the running total overflows
            // part-way through this pass (it may come back down once later lanes improve).
            boolean recount = overflowed[depth];

            for (int lane = 0; lane < laneCount; lane++) {
                long previous = parentQuote[lane];
                long offered = matrix.amount(lane, t);
                // Strict '<': on an equal quote keep the existing owner. Members are added in ascending id order,
                // so the existing owner always has the lower id.
                if (offered != NO_QUOTE && (previous == NO_QUOTE || offered < previous)) {
                    childQuote[lane] = offered;
                    childOwner[lane] = t;
                    if (previous == NO_QUOTE) {
                        uncovered--;
                        if (!recount) {
                            if (offered > Long.MAX_VALUE - cost) {
                                recount = true;
                            } else {
                                cost += offered;
                            }
                        }
                    } else if (!recount) {
                        cost -= previous - offered; // exact: 0 < offered < previous <= cost
                    }
                } else {
                    childQuote[lane] = previous;
                    childOwner[lane] = parentOwner[lane];
                }
            }
            if (recount) {
                cost = exactSumOrOverflow(childQuote);
            }
            totalCost[depth + 1] = cost;
            overflowed[depth + 1] = cost == OVERFLOW;
            uncoveredLanes[depth + 1] = uncovered;
        }

        private static final long OVERFLOW = -1;

        /** Sum of the per-lane quotes (NO_QUOTE adds nothing), or {@link #OVERFLOW} if it exceeds Long.MAX_VALUE. */
        private static long exactSumOrOverflow(long[] quotes) {
            long sum = 0;
            try {
                for (long quote : quotes) {
                    sum = Math.addExact(sum, quote);
                }
            } catch (ArithmeticException overflow) {
                return OVERFLOW; // all terms are non-negative, so an overflowing prefix means the total overflows
            }
            return sum;
        }

        private void considerCurrent(int size) {
            if (uncoveredLanes[size] != 0) {
                return;
            }
            if (overflowed[size]) {
                sawFeasibleOverflow = true;
                return;
            }
            long cost = totalCost[size];
            // Equal cost and equal size: keep the incumbent. Depth-first ascending order visits same-size
            // combinations in lexicographic order, so the incumbent has the smaller id list.
            boolean better = !found
                    || cost < bestCost
                    || (cost == bestCost && size < bestMembers.length);
            if (better) {
                found = true;
                bestCost = cost;
                bestMembers = Arrays.copyOf(members, size);
                bestOwners = bestOwner[size].clone();
            }
        }

        boolean foundFeasible() {
            return found;
        }

        boolean foundOnlyUnrepresentableTotals() {
            return !found && sawFeasibleOverflow;
        }

        AssignmentSolution bestSolution() {
            List<LaneAssignment> assignments = new ArrayList<>(laneCount);
            for (int lane = 0; lane < laneCount; lane++) {
                int owner = bestOwners[lane];
                assignments.add(new LaneAssignment(
                        matrix.laneId(lane), matrix.transporterId(owner), matrix.amount(lane, owner)));
            }
            List<Long> selected = new ArrayList<>(bestMembers.length);
            for (int member : bestMembers) {
                selected.add(matrix.transporterId(member));
            }
            return new AssignmentSolution(bestCost, assignments, selected);
        }
    }
}
