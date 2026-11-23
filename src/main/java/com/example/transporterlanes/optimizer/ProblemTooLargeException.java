package com.example.transporterlanes.optimizer;

/**
 * The problem exceeds one of the solver's configured size limits. Thrown before any large allocation or search.
 * Intended to map to HTTP 422.
 */
public class ProblemTooLargeException extends RuntimeException {

    /** Which safeguard was exceeded. */
    public enum Limit {
        /** Combinations of up to K transporters x lanes: bounds running time. */
        LANE_EVALUATIONS,
        /** Lanes x transporters in the internal price table: bounds memory. */
        MATRIX_CELLS
    }

    private final Limit limit;
    private final long required;
    private final long maximum;

    private ProblemTooLargeException(Limit limit, long required, long maximum, String message) {
        super(message);
        this.limit = limit;
        this.required = required;
        this.maximum = maximum;
    }

    /** @param requiredLaneEvaluations exact count, or Long.MAX_VALUE if it does not fit in a long */
    static ProblemTooLargeException searchTooLarge(long requiredLaneEvaluations, long maxLaneEvaluations,
                                                   int maxTransporters, int laneCount) {
        return new ProblemTooLargeException(Limit.LANE_EVALUATIONS, requiredLaneEvaluations, maxLaneEvaluations,
                "Search space too large: " + describe(requiredLaneEvaluations)
                        + " lane evaluations needed (combinations of up to " + maxTransporters + " transporter(s) x "
                        + laneCount + " lane(s)), limit is " + maxLaneEvaluations
                        + ". Reduce maxTransporters or the number of transporters.");
    }

    static ProblemTooLargeException matrixTooLarge(int laneCount, int transporterCount, long cells, long maxCells) {
        return new ProblemTooLargeException(Limit.MATRIX_CELLS, cells, maxCells,
                "Problem too large: " + laneCount + " lane(s) x " + transporterCount + " transporter(s) = "
                        + cells + " price-table cells, limit is " + maxCells
                        + ". Reduce the number of lanes or transporters.");
    }

    private static String describe(long count) {
        return count == Long.MAX_VALUE ? "more than " + Long.MAX_VALUE + " (the largest representable count)"
                : String.valueOf(count);
    }

    public Limit getLimit() {
        return limit;
    }

    /** The size the problem needs; Long.MAX_VALUE means "more than can be represented". */
    public long getRequired() {
        return required;
    }

    public long getMaximum() {
        return maximum;
    }
}
