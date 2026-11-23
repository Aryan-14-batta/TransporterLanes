package com.example.transporterlanes.optimizer;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OptimizationProblemTest {

    private static final List<Long> LANES = List.of(1L, 2L);
    private static final List<Long> TRANSPORTERS = List.of(10L, 20L);
    private static final List<Quote> QUOTES = List.of(new Quote(1, 10, 100), new Quote(2, 20, 200));

    @Test
    void acceptsValidProblem() {
        assertThatCode(() -> new OptimizationProblem(LANES, TRANSPORTERS, QUOTES, 2)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void rejectsMaxTransportersBelowOne(int k) {
        assertThatIllegalArgumentException().isThrownBy(() -> new OptimizationProblem(LANES, TRANSPORTERS, QUOTES, k))
                .withMessageContaining("maxTransporters must be at least 1");
    }

    @Test
    void rejectsEmptyLanes() {
        assertThatIllegalArgumentException().isThrownBy(() -> new OptimizationProblem(List.of(), TRANSPORTERS, List.of(), 1))
                .withMessageContaining("At least one lane");
    }

    @Test
    void rejectsEmptyTransporters() {
        assertThatIllegalArgumentException().isThrownBy(() -> new OptimizationProblem(LANES, List.of(), List.of(), 1))
                .withMessageContaining("At least one transporter");
    }

    @Test
    void rejectsDuplicateLaneIds() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new OptimizationProblem(List.of(1L, 1L), TRANSPORTERS, List.of(), 1))
                .withMessageContaining("Duplicate lane id: 1");
    }

    @Test
    void rejectsDuplicateTransporterIds() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new OptimizationProblem(LANES, List.of(10L, 10L), List.of(), 1))
                .withMessageContaining("Duplicate transporter id: 10");
    }

    @Test
    void rejectsQuoteForUnknownLane() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new OptimizationProblem(LANES, TRANSPORTERS, List.of(new Quote(9, 10, 5)), 1))
                .withMessageContaining("unknown lane id: 9");
    }

    @Test
    void rejectsQuoteForUnknownTransporter() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new OptimizationProblem(LANES, TRANSPORTERS, List.of(new Quote(1, 99, 5)), 1))
                .withMessageContaining("unknown transporter id: 99");
    }

    @Test
    void rejectsDuplicateQuoteForSameLaneAndTransporter() {
        List<Quote> duplicated = List.of(new Quote(1, 10, 5), new Quote(1, 10, 7));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new OptimizationProblem(LANES, TRANSPORTERS, duplicated, 1))
                .withMessageContaining("Duplicate quote for lane 1 by transporter 10");
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void rejectsNonPositiveQuoteAmount(long amount) {
        assertThatIllegalArgumentException().isThrownBy(() -> new Quote(1, 10, amount))
                .withMessageContaining("must be positive");
    }

    @Test
    void isImmutable() {
        var lanes = new java.util.ArrayList<>(LANES);
        OptimizationProblem problem = new OptimizationProblem(lanes, TRANSPORTERS, QUOTES, 1);
        lanes.add(3L);
        assertThatCode(() -> problem.laneIds().add(4L)).isInstanceOf(UnsupportedOperationException.class);
        org.assertj.core.api.Assertions.assertThat(problem.laneIds()).containsExactly(1L, 2L);
    }
}
