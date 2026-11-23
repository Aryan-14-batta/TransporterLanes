package com.example.transporterlanes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A transporter's price for one lane. Owns both relationships; Lane and Transporter have no collections, which keeps
 * loading and replacing the dataset to a few simple queries.
 */
@Entity
@Table(name = "lane_quote")
public class LaneQuote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lane_id", nullable = false)
    private Lane lane;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transporter_id", nullable = false)
    private Transporter transporter;

    @Column(nullable = false)
    private long quote;

    protected LaneQuote() {
        // for JPA
    }

    public LaneQuote(Lane lane, Transporter transporter, long quote) {
        this.lane = lane;
        this.transporter = transporter;
        this.quote = quote;
    }

    public Long getId() {
        return id;
    }

    public Lane getLane() {
        return lane;
    }

    public Transporter getTransporter() {
        return transporter;
    }

    public long getQuote() {
        return quote;
    }
}
