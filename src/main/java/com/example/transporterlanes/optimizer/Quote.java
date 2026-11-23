package com.example.transporterlanes.optimizer;

/**
 * A transporter's price for serving one lane. Amounts are whole currency units (e.g. rupees) and must be positive.
 */
public record Quote(long laneId, long transporterId, long amount) {

    public Quote {
        if (amount <= 0) {
            throw new IllegalArgumentException(
                    "Quote amount must be positive (lane " + laneId + ", transporter " + transporterId + "), was " + amount);
        }
    }
}
