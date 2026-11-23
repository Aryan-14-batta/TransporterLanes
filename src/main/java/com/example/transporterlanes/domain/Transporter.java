package com.example.transporterlanes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A transporter. The id is supplied by the client, not generated. */
@Entity
@Table(name = "transporter")
public class Transporter {

    @Id
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    protected Transporter() {
        // for JPA
    }

    public Transporter(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
