package com.example.transporterlanes.repository;

/** Flat read model of a lane quote: just the ids and amount, without loading Lane or Transporter entities. */
public record QuoteRow(long laneId, long transporterId, long quote) {
}
