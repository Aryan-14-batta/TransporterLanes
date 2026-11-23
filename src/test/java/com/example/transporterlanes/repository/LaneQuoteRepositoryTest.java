package com.example.transporterlanes.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.example.transporterlanes.domain.Lane;
import com.example.transporterlanes.domain.LaneQuote;
import com.example.transporterlanes.domain.Transporter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** Runs the real Flyway migration on H2 (PostgreSQL mode) and checks the schema's constraints. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // use the Flyway-managed test DB
class LaneQuoteRepositoryTest {

    @Autowired
    private LaneRepository laneRepository;
    @Autowired
    private TransporterRepository transporterRepository;
    @Autowired
    private LaneQuoteRepository laneQuoteRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private Lane lane;
    private Transporter transporter;

    @BeforeEach
    void setUp() {
        lane = laneRepository.saveAndFlush(new Lane(1L, "Mumbai", "Delhi"));
        transporter = transporterRepository.saveAndFlush(new Transporter(10L, "Transporter T10"));
    }

    @Test
    void quoteRowsExposeIdsAndAmount() {
        Lane secondLane = laneRepository.saveAndFlush(new Lane(2L, "Delhi", "Kolkata"));
        laneQuoteRepository.saveAndFlush(new LaneQuote(lane, transporter, 5_000));
        laneQuoteRepository.saveAndFlush(new LaneQuote(secondLane, transporter, 7_000));

        assertThat(laneQuoteRepository.findAllQuoteRows())
                .containsExactlyInAnyOrder(new QuoteRow(1, 10, 5_000), new QuoteRow(2, 10, 7_000));
    }

    @Test
    void secondQuoteForSameLaneAndTransporterViolatesUniqueConstraint() {
        laneQuoteRepository.saveAndFlush(new LaneQuote(lane, transporter, 5_000));

        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(
                () -> laneQuoteRepository.saveAndFlush(new LaneQuote(lane, transporter, 6_000)));
    }

    @Test
    void nonPositiveQuoteViolatesCheckConstraint() {
        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(
                () -> laneQuoteRepository.saveAndFlush(new LaneQuote(lane, transporter, 0)));
    }

    @Test
    void quoteAboveOneLakhCroreViolatesCheckConstraint() {
        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(
                () -> laneQuoteRepository.saveAndFlush(new LaneQuote(lane, transporter, 1_000_000_000_001L)));
    }

    @Test
    void quoteForMissingLaneViolatesForeignKey() {
        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() -> jdbc.update(
                "insert into lane_quote (lane_id, transporter_id, quote) values (999, 10, 5)"));
    }

    @Test
    void laneStillReferencedByQuotesCannotBeDeleted() {
        laneQuoteRepository.saveAndFlush(new LaneQuote(lane, transporter, 5_000));

        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() -> jdbc.update(
                "delete from lane where id = 1"));
    }

    @Test
    void nonPositiveLaneIdViolatesCheckConstraint() {
        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(
                () -> laneRepository.saveAndFlush(new Lane(0L, "A", "B")));
    }
}
