package com.example.transporterlanes.repository;

import com.example.transporterlanes.domain.Transporter;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransporterRepository extends JpaRepository<Transporter, Long> {
}
