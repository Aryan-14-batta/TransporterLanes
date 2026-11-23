package com.example.transporterlanes.optimizer;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable, validated input to an {@link AssignmentSolver}.
 *
 * <p>A transporter does not have to quote every lane; a missing quote means that transporter cannot serve that lane.
 * {@code maxTransporters} is an upper limit on how many transporters the solution may use, not an exact count.
 *
 * @throws IllegalArgumentException if the input is structurally invalid (empty, duplicate ids, unknown references,
 *                                  duplicate quotes, non-positive amounts, or {@code maxTransporters < 1})
 */
public record OptimizationProblem(
        List<Long> laneIds,
        List<Long> transporterIds,
        List<Quote> quotes,
        int maxTransporters) {

    public OptimizationProblem {
        // List.copyOf makes the record immutable and rejects null elements.
        laneIds = List.copyOf(Objects.requireNonNull(laneIds, "laneIds"));
        transporterIds = List.copyOf(Objects.requireNonNull(transporterIds, "transporterIds"));
        quotes = List.copyOf(Objects.requireNonNull(quotes, "quotes"));

        require(!laneIds.isEmpty(), "At least one lane is required");
        require(!transporterIds.isEmpty(), "At least one transporter is required");
        require(maxTransporters >= 1, "maxTransporters must be at least 1, was " + maxTransporters);

        Set<Long> knownLanes = uniqueIds(laneIds, "lane");
        Set<Long> knownTransporters = uniqueIds(transporterIds, "transporter");
        validateQuotes(quotes, knownLanes, knownTransporters);
    }

    private static Set<Long> uniqueIds(List<Long> ids, String kind) {
        Set<Long> unique = new HashSet<>();
        for (Long id : ids) {
            require(unique.add(id), "Duplicate " + kind + " id: " + id);
        }
        return unique;
    }

    private static void validateQuotes(List<Quote> quotes, Set<Long> knownLanes, Set<Long> knownTransporters) {
        record LaneTransporter(long laneId, long transporterId) {
        }
        Set<LaneTransporter> seen = new HashSet<>();
        for (Quote quote : quotes) {
            require(knownLanes.contains(quote.laneId()), "Quote references unknown lane id: " + quote.laneId());
            require(knownTransporters.contains(quote.transporterId()),
                    "Quote references unknown transporter id: " + quote.transporterId());
            require(seen.add(new LaneTransporter(quote.laneId(), quote.transporterId())),
                    "Duplicate quote for lane " + quote.laneId() + " by transporter " + quote.transporterId());
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
