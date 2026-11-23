package com.example.transporterlanes.config;

import com.example.transporterlanes.optimizer.AssignmentSolver;
import com.example.transporterlanes.optimizer.ExactCombinationSolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the plain-Java optimizer into Spring. The optimizer itself has no Spring dependency. */
@Configuration
public class OptimizerConfig {

    @Bean
    public AssignmentSolver assignmentSolver(
            @Value("${transporter-lanes.optimizer.max-lane-evaluations}") long maxLaneEvaluations,
            @Value("${transporter-lanes.optimizer.max-matrix-cells}") long maxMatrixCells) {
        return new ExactCombinationSolver(maxLaneEvaluations, maxMatrixCells);
    }
}
