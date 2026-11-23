package com.example.transporterlanes.service;

import com.example.transporterlanes.domain.Lane;
import com.example.transporterlanes.domain.Transporter;
import com.example.transporterlanes.dto.AssignmentResponse;
import com.example.transporterlanes.dto.LaneAssignmentResponse;
import com.example.transporterlanes.exception.NoInputDataException;
import com.example.transporterlanes.exception.UnrepresentableTotalCostException;
import com.example.transporterlanes.optimizer.AssignmentSolution;
import com.example.transporterlanes.optimizer.AssignmentSolver;
import com.example.transporterlanes.optimizer.OptimizationProblem;
import com.example.transporterlanes.optimizer.Quote;
import com.example.transporterlanes.repository.LaneQuoteRepository;
import com.example.transporterlanes.repository.LaneRepository;
import com.example.transporterlanes.repository.TransporterRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssignmentService {

    private final LaneRepository laneRepository;
    private final TransporterRepository transporterRepository;
    private final LaneQuoteRepository laneQuoteRepository;
    private final AssignmentSolver solver;

    public AssignmentService(LaneRepository laneRepository, TransporterRepository transporterRepository,
                             LaneQuoteRepository laneQuoteRepository, AssignmentSolver solver) {
        this.laneRepository = laneRepository;
        this.transporterRepository = transporterRepository;
        this.laneQuoteRepository = laneQuoteRepository;
        this.solver = solver;
    }

    /**
     * Computes the optimal assignment over the stored dataset.
     *
     * <p>REPEATABLE_READ gives the three reads one consistent snapshot, so a dataset replaced concurrently is seen
     * either entirely before or entirely after, never half of each.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AssignmentResponse assign(int maxTransporters) {
        List<Long> laneIds = laneRepository.findAll().stream().map(Lane::getId).toList();
        if (laneIds.isEmpty()) {
            throw new NoInputDataException();
        }
        List<Long> transporterIds = transporterRepository.findAll().stream().map(Transporter::getId).toList();
        List<Quote> quotes = laneQuoteRepository.findAllQuoteRows().stream()
                .map(row -> new Quote(row.laneId(), row.transporterId(), row.quote()))
                .toList();

        OptimizationProblem problem = new OptimizationProblem(laneIds, transporterIds, quotes, maxTransporters);
        return toResponse(solve(problem));
    }

    private AssignmentSolution solve(OptimizationProblem problem) {
        try {
            return solver.solve(problem);
        } catch (ArithmeticException everyFeasibleTotalOverflows) {
            // Scoped to the solver call only: other arithmetic errors stay unexpected (HTTP 500).
            throw new UnrepresentableTotalCostException(everyFeasibleTotalOverflows);
        }
    }

    private static AssignmentResponse toResponse(AssignmentSolution solution) {
        List<LaneAssignmentResponse> assignments = solution.assignments().stream()
                .map(a -> new LaneAssignmentResponse(a.laneId(), a.transporterId()))
                .toList();
        return AssignmentResponse.success(solution.totalCost(), assignments, solution.selectedTransporterIds());
    }
}
