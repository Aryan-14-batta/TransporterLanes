package com.example.transporterlanes.optimizer;

/** One lane served by one transporter at the quoted amount. */
public record LaneAssignment(long laneId, long transporterId, long quote) {
}
