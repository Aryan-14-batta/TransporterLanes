package com.example.transporterlanes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A trade lane. The id is supplied by the client, not generated. */
@Entity
@Table(name = "lane")
public class Lane {

    @Id
    private Long id;

    @Column(nullable = false, length = 100)
    private String origin;

    @Column(nullable = false, length = 100)
    private String destination;

    protected Lane() {
        // for JPA
    }

    public Lane(Long id, String origin, String destination) {
        this.id = id;
        this.origin = origin;
        this.destination = destination;
    }

    public Long getId() {
        return id;
    }

    public String getOrigin() {
        return origin;
    }

    public String getDestination() {
        return destination;
    }
}
