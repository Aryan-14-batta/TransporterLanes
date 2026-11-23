package com.example.transporterlanes.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.transporterlanes.domain.Lane;
import com.example.transporterlanes.domain.Transporter;
import com.example.transporterlanes.dto.AssignmentResponse;
import com.example.transporterlanes.dto.LaneAssignmentResponse;
import com.example.transporterlanes.exception.NoInputDataException;
import com.example.transporterlanes.exception.UnrepresentableTotalCostException;
import com.example.transporterlanes.optimizer.AssignmentSolution;
import com.example.transporterlanes.optimizer.AssignmentSolver;
import com.example.transporterlanes.optimizer.LaneAssignment;
import com.example.transporterlanes.optimizer.NoFeasibleAssignmentException;
import com.example.transporterlanes.optimizer.OptimizationProblem;
import com.example.transporterlanes.optimizer.Quote;
import com.example.transporterlanes.repository.LaneQuoteRepository;
import com.example.transporterlanes.repository.LaneRepository;
import com.example.transporterlanes.repository.QuoteRow;
import com.example.transporterlanes.repository.TransporterRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssignmentServiceTest {

    @Mock
    private LaneRepository laneRepository;
    @Mock
    private TransporterRepository transporterRepository;
    @Mock
    private LaneQuoteRepository laneQuoteRepository;
    @Mock
    private AssignmentSolver solver;
    @Captor
    private ArgumentCaptor<OptimizationProblem> problem;

    private AssignmentService service;

    @BeforeEach
    void setUp() {
        service = new AssignmentService(laneRepository, transporterRepository, laneQuoteRepository, solver);
    }

    private void storedDataset() {
        when(laneRepository.findAll()).thenReturn(List.of(new Lane(1L, "A", "B"), new Lane(2L, "C", "D")));
        when(transporterRepository.findAll()).thenReturn(
                List.of(new Transporter(10L, "T10"), new Transporter(20L, "T20")));
        when(laneQuoteRepository.findAllQuoteRows()).thenReturn(List.of(
                new QuoteRow(1, 10, 100), new QuoteRow(2, 20, 200), new QuoteRow(1, 20, 150)));
    }

    @Test
    void noStoredLanesMeansNoInputData() {
        when(laneRepository.findAll()).thenReturn(List.of());

        assertThatExceptionOfType(NoInputDataException.class).isThrownBy(() -> service.assign(3));
        verifyNoInteractions(solver);
    }

    @Test
    void buildsTheProblemFromStoredDataAndMapsTheSolution() {
        storedDataset();
        when(solver.solve(any())).thenReturn(new AssignmentSolution(300,
                List.of(new LaneAssignment(1, 10, 100), new LaneAssignment(2, 20, 200)), List.of(10L, 20L)));

        AssignmentResponse response = service.assign(2);

        verify(solver).solve(problem.capture());
        assertThat(problem.getValue().laneIds()).containsExactly(1L, 2L);
        assertThat(problem.getValue().transporterIds()).containsExactly(10L, 20L);
        assertThat(problem.getValue().quotes()).containsExactly(
                new Quote(1, 10, 100), new Quote(2, 20, 200), new Quote(1, 20, 150));
        assertThat(problem.getValue().maxTransporters()).isEqualTo(2);

        assertThat(response).isEqualTo(new AssignmentResponse("success", 300,
                List.of(new LaneAssignmentResponse(1, 10), new LaneAssignmentResponse(2, 20)), List.of(10L, 20L)));
    }

    @Test
    void solverOverflowBecomesADomainException() {
        storedDataset();
        ArithmeticException overflow = new ArithmeticException("every total overflows");
        when(solver.solve(any())).thenThrow(overflow);

        assertThatExceptionOfType(UnrepresentableTotalCostException.class)
                .isThrownBy(() -> service.assign(2))
                .withMessage("every total overflows")
                .withCause(overflow);
    }

    @Test
    void infeasibilityFromTheSolverPropagatesUnchanged() {
        storedDataset();
        var infeasible = new NoFeasibleAssignmentException(1, List.of(), "no combination");
        when(solver.solve(any())).thenThrow(infeasible);

        assertThatExceptionOfType(NoFeasibleAssignmentException.class)
                .isThrownBy(() -> service.assign(1))
                .satisfies(e -> assertThat(e).isSameAs(infeasible));
    }
}
