package com.example.transporterlanes.optimizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Builds problems as a readable price table:
 * <pre>
 *   ProblemBuilder.lanes(1, 2)
 *       .transporter(1, 4, null)   // quotes for lane 1 and lane 2; null = no quote
 *       .transporter(2, 6, 3)
 *       .build(2);                 // maxTransporters
 * </pre>
 */
final class ProblemBuilder {

    private final List<Long> laneIds;
    private final List<Long> transporterIds = new ArrayList<>();
    private final List<Quote> quotes = new ArrayList<>();

    private ProblemBuilder(List<Long> laneIds) {
        this.laneIds = laneIds;
    }

    static ProblemBuilder lanes(long... laneIds) {
        return new ProblemBuilder(Arrays.stream(laneIds).boxed().toList());
    }

    ProblemBuilder transporter(long transporterId, Integer... quotePerLane) {
        if (quotePerLane.length != laneIds.size()) {
            throw new IllegalArgumentException("Expected one quote (or null) per lane");
        }
        transporterIds.add(transporterId);
        for (int i = 0; i < quotePerLane.length; i++) {
            if (quotePerLane[i] != null) {
                quotes.add(new Quote(laneIds.get(i), transporterId, quotePerLane[i]));
            }
        }
        return this;
    }

    OptimizationProblem build(int maxTransporters) {
        return new OptimizationProblem(laneIds, transporterIds, quotes, maxTransporters);
    }
}
