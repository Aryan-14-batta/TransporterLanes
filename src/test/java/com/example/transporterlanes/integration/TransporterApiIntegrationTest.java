package com.example.transporterlanes.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.transporterlanes.TestRequests;
import com.example.transporterlanes.dto.AssignmentResponse;
import com.example.transporterlanes.dto.InputRequest;
import com.example.transporterlanes.dto.LaneAssignmentResponse;
import com.example.transporterlanes.repository.LaneQuoteRepository;
import com.example.transporterlanes.repository.LaneRepository;
import com.example.transporterlanes.repository.TransporterRepository;
import com.example.transporterlanes.service.TransporterInputService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Full stack: HTTP -> controller -> services -> JPA -> Flyway-managed H2 (PostgreSQL mode) -> optimizer. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransporterApiIntegrationTest {

    private static final String INPUT = "/api/v1/transporters/input";
    private static final String ASSIGNMENT = "/api/v1/transporters/assignment";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private TransporterInputService inputService;
    @MockitoSpyBean // delegates to the real repository unless a test stubs it
    private LaneRepository laneRepository;
    @Autowired
    private TransporterRepository transporterRepository;
    @Autowired
    private LaneQuoteRepository laneQuoteRepository;

    @BeforeEach
    void emptyDatabase() {
        laneQuoteRepository.deleteAllInBatch();
        transporterRepository.deleteAllInBatch();
        laneRepository.deleteAllInBatch();
    }

    private ResultActions postJson(String path, String body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String assignmentDataset() throws IOException {
        return new ClassPathResource("datasets/assignment-dataset.json").getContentAsString(StandardCharsets.UTF_8);
    }

    private AssignmentResponse assign(int k) throws Exception {
        String body = postJson(ASSIGNMENT, "{\"maxTransporters\": " + k + "}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readValue(body, AssignmentResponse.class);
    }

    private void storedDatasetIsTheAssignmentDataset() throws Exception {
        assertThat(laneRepository.count()).isEqualTo(9);
        assertThat(transporterRepository.count()).isEqualTo(7);
        assertThat(laneQuoteRepository.count()).isEqualTo(63);
        assertThat(assign(3).totalCost()).isEqualTo(134_876);
    }

    @Test
    void assignmentBeforeAnyInputIs404() throws Exception {
        postJson(ASSIGNMENT, "{\"maxTransporters\": 3}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("error"));
    }

    static Stream<Arguments> expectedResults() {
        // Same independently computed expectations as the optimizer's CsvRegressionTest.
        return Stream.of(
                Arguments.of(1, 201_259L, List.of(1L), new long[] {1, 1, 1, 1, 1, 1, 1, 1, 1}),
                Arguments.of(2, 148_402L, List.of(1L, 4L), new long[] {4, 1, 4, 4, 4, 1, 1, 1, 1}),
                Arguments.of(3, 134_876L, List.of(1L, 4L, 6L), new long[] {4, 1, 4, 4, 4, 1, 1, 6, 6}),
                Arguments.of(4, 127_844L, List.of(1L, 3L, 4L, 7L), new long[] {4, 1, 4, 4, 3, 1, 1, 3, 7}),
                Arguments.of(5, 125_045L, List.of(1L, 3L, 4L, 5L, 7L), new long[] {5, 1, 4, 4, 3, 1, 1, 3, 7}),
                Arguments.of(7, 125_045L, List.of(1L, 3L, 4L, 5L, 7L), new long[] {5, 1, 4, 4, 3, 1, 1, 3, 7}),
                Arguments.of(50, 125_045L, List.of(1L, 3L, 4L, 5L, 7L), new long[] {5, 1, 4, 4, 3, 1, 1, 3, 7}));
    }

    @ParameterizedTest(name = "K={0}: cost {1}, transporters {2}")
    @MethodSource("expectedResults")
    void assignmentDatasetEndToEnd(int k, long cost, List<Long> selected, long[] transporterByLane) throws Exception {
        postJson(INPUT, assignmentDataset())
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"status": "success", "message": "Input data saved successfully."}""", true));

        AssignmentResponse response = assign(k);

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.totalCost()).isEqualTo(cost);
        assertThat(response.selectedTransporters()).containsExactlyElementsOf(selected);
        assertThat(response.assignments()).containsExactlyElementsOf(IntStream.range(0, 9)
                .mapToObj(i -> new LaneAssignmentResponse(i + 1, transporterByLane[i])).toList());
    }

    @Test
    void secondInputReplacesTheFirstEntirely() throws Exception {
        postJson(INPUT, assignmentDataset()).andExpect(status().isOk());
        postJson(INPUT, json.writeValueAsString(TestRequests.twoLaneDataset())).andExpect(status().isOk());

        assertThat(laneRepository.findAll()).extracting(lane -> lane.getId()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(transporterRepository.findAll()).extracting(t -> t.getId()).containsExactlyInAnyOrder(10L, 20L);
        assertThat(laneQuoteRepository.count()).isEqualTo(2);
        AssignmentResponse response = assign(2);
        assertThat(response.totalCost()).isEqualTo(300);
        assertThat(response.selectedTransporters()).containsExactly(10L, 20L);
    }

    @Test
    void infeasibleLimitIs422() throws Exception {
        postJson(INPUT, json.writeValueAsString(TestRequests.twoLaneDataset())).andExpect(status().isOk());

        postJson(ASSIGNMENT, "{\"maxTransporters\": 1}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("No combination of at most 1 transporter(s) covers all 2 lanes."));
    }

    @Test
    void rejectedInputLeavesThePreviousDatasetUntouched() throws Exception {
        postJson(INPUT, assignmentDataset()).andExpect(status().isOk());
        String unknownLane = """
                {"lanes": [{"id": 1, "origin": "A", "destination": "B"}],
                 "transporters": [{"id": 1, "name": "T1", "laneQuotes": [{"laneId": 99, "quote": 5}]}]}""";

        postJson(INPUT, unknownLane)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(org.hamcrest.Matchers.containsInAnyOrder(
                        "transporters[0].laneQuotes[0].laneId", "lanes[0]")));

        storedDatasetIsTheAssignmentDataset();
    }

    @Test
    void failureDuringInsertionRollsBackDeletionAndEarlierInserts() throws Exception {
        postJson(INPUT, assignmentDataset()).andExpect(status().isOk());
        // Bypasses HTTP Bean Validation so the database CHECK constraint fails on the last insert (the quotes),
        // after the old data was deleted and the new lanes and transporters were inserted.
        InputRequest zeroQuote = TestRequests.input(
                List.of(TestRequests.lane(100)),
                List.of(TestRequests.transporter(200, TestRequests.quote(100, 0))));

        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> inputService.replaceDataset(zeroQuote));

        assertThat(laneRepository.existsById(100L)).isFalse();
        assertThat(transporterRepository.existsById(200L)).isFalse();
        storedDatasetIsTheAssignmentDataset();
    }

    @Test
    void failureDuringDeletionRollsBackEarlierDeletions() throws Exception {
        postJson(INPUT, assignmentDataset()).andExpect(status().isOk());
        // Quotes and transporters are deleted first; then deleting lanes fails.
        doThrow(new IllegalStateException("simulated failure")).when(laneRepository).deleteAllInBatch();

        postJson(INPUT, json.writeValueAsString(TestRequests.twoLaneDataset()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred."));

        org.mockito.Mockito.reset(laneRepository);
        storedDatasetIsTheAssignmentDataset();
    }
}
