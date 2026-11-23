package com.example.transporterlanes.service;

import static com.example.transporterlanes.TestRequests.input;
import static com.example.transporterlanes.TestRequests.lane;
import static com.example.transporterlanes.TestRequests.quote;
import static com.example.transporterlanes.TestRequests.transporter;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.transporterlanes.TestRequests;
import com.example.transporterlanes.dto.ErrorResponse.FieldError;
import java.util.List;
import org.junit.jupiter.api.Test;

class InputDataValidatorTest {

    private final InputDataValidator validator = new InputDataValidator();

    @Test
    void acceptsConsistentInput() {
        assertThat(validator.validate(TestRequests.twoLaneDataset())).isEmpty();
    }

    @Test
    void acceptsTransporterWithNoQuotesAndLanesQuotedByManyTransporters() {
        var request = input(List.of(lane(1)),
                List.of(transporter(1, quote(1, 5)), transporter(2, quote(1, 6)), transporter(3)));

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsDuplicateLaneId() {
        var request = input(List.of(lane(1), lane(1)), List.of(transporter(1, quote(1, 5))));

        assertThat(validator.validate(request)).containsExactly(
                new FieldError("lanes[1].id", "Duplicate lane id 1 (already used by lanes[0])"));
    }

    @Test
    void rejectsDuplicateTransporterId() {
        var request = input(List.of(lane(1)), List.of(transporter(7, quote(1, 5)), transporter(7, quote(1, 6))));

        assertThat(validator.validate(request)).containsExactly(
                new FieldError("transporters[1].id", "Duplicate transporter id 7 (already used by transporters[0])"));
    }

    @Test
    void rejectsQuoteForUnknownLane() {
        var request = input(List.of(lane(1)), List.of(transporter(1, quote(1, 5), quote(99, 5))));

        assertThat(validator.validate(request)).containsExactly(
                new FieldError("transporters[0].laneQuotes[1].laneId", "Unknown lane id 99"));
    }

    @Test
    void rejectsSecondQuoteForSameLaneBySameTransporter() {
        var request = input(List.of(lane(1)), List.of(transporter(1, quote(1, 5), quote(1, 7))));

        assertThat(validator.validate(request)).containsExactly(
                new FieldError("transporters[0].laneQuotes[1].laneId", "Duplicate quote for lane 1"));
    }

    @Test
    void rejectsLaneThatNoTransporterQuotes() {
        var request = input(List.of(lane(1), lane(2)), List.of(transporter(1, quote(1, 5))));

        assertThat(validator.validate(request)).containsExactly(new FieldError("lanes[1]",
                "Lane 2 has no quotes; every lane must be quoted by at least one transporter"));
    }

    @Test
    void reportsEveryProblemAtOnceAndEachUnquotedLaneOnlyOnce() {
        var request = input(
                List.of(lane(1), lane(2), lane(2)),
                List.of(transporter(1, quote(1, 5), quote(3, 5)), transporter(1)));

        assertThat(validator.validate(request)).extracting(FieldError::field).containsExactlyInAnyOrder(
                "lanes[2].id",                           // duplicate lane 2
                "transporters[1].id",                    // duplicate transporter 1
                "transporters[0].laneQuotes[1].laneId",  // unknown lane 3
                "lanes[1]");                             // lane 2 unquoted, reported once
    }
}
