package com.example.transporterlanes.repository;

import com.example.transporterlanes.domain.Lane;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LaneRepository extends JpaRepository<Lane, Long> {
}
