package com.example.transporterlanes.optimizer;

import java.util.List;

/** No combination of at most K transporters covers every lane. Intended to map to HTTP 422. */
public class NoFeasibleAssignmentException extends RuntimeException {

    private final int maxTransporters;
    private final List<Long> lanesWithoutQuotes;

    public NoFeasibleAssignmentException(int maxTransporters, List<Long> lanesWithoutQuotes, String message) {
        super(message);
        this.maxTransporters = maxTransporters;
        this.lanesWithoutQuotes = List.copyOf(lanesWithoutQuotes);
    }

    /** The effective limit that was searched (after capping to the number of transporters). */
    public int getMaxTransporters() {
        return maxTransporters;
    }

    /** Lanes that no transporter quotes at all; empty when the limit K is the only reason for infeasibility. */
    public List<Long> getLanesWithoutQuotes() {
        return lanesWithoutQuotes;
    }
}
