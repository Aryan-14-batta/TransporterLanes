package com.example.transporterlanes.optimizer;

import java.util.HashMap;
import java.util.Map;

/**
 * Dense lane x transporter price table used internally by the solver.
 *
 * <p>Lanes and transporters are both sorted by ascending id, so a lower transporter index always means a lower id.
 * The solver relies on this for its tie-breaking rules.
 */
final class QuoteMatrix {

    /** Marks a missing quote. Safe because real quotes are always positive. */
    static final long NO_QUOTE = 0;

    private final long[] laneIds;
    private final long[] transporterIds;
    private final long[][] amounts; // [laneIndex][transporterIndex]

    private QuoteMatrix(long[] laneIds, long[] transporterIds, long[][] amounts) {
        this.laneIds = laneIds;
        this.transporterIds = transporterIds;
        this.amounts = amounts;
    }

    static QuoteMatrix from(OptimizationProblem problem) {
        long[] laneIds = problem.laneIds().stream().mapToLong(Long::longValue).sorted().toArray();
        long[] transporterIds = problem.transporterIds().stream().mapToLong(Long::longValue).sorted().toArray();
        Map<Long, Integer> laneIndex = indexById(laneIds);
        Map<Long, Integer> transporterIndex = indexById(transporterIds);

        long[][] amounts = new long[laneIds.length][transporterIds.length]; // zero-filled == NO_QUOTE
        for (Quote quote : problem.quotes()) {
            amounts[laneIndex.get(quote.laneId())][transporterIndex.get(quote.transporterId())] = quote.amount();
        }
        return new QuoteMatrix(laneIds, transporterIds, amounts);
    }

    private static Map<Long, Integer> indexById(long[] ids) {
        Map<Long, Integer> index = new HashMap<>();
        for (int i = 0; i < ids.length; i++) {
            index.put(ids[i], i);
        }
        return index;
    }

    int laneCount() {
        return laneIds.length;
    }

    int transporterCount() {
        return transporterIds.length;
    }

    long laneId(int laneIndex) {
        return laneIds[laneIndex];
    }

    long transporterId(int transporterIndex) {
        return transporterIds[transporterIndex];
    }

    /** The quote, or {@link #NO_QUOTE} if this transporter does not serve this lane. */
    long amount(int laneIndex, int transporterIndex) {
        return amounts[laneIndex][transporterIndex];
    }
}
