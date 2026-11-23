package com.example.transporterlanes.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.transporterlanes.optimizer.ExactCombinationSolver;
import com.example.transporterlanes.optimizer.OptimizationProblem;
import com.example.transporterlanes.optimizer.Quote;
import com.example.transporterlanes.repository.LaneQuoteRepository;
import com.example.transporterlanes.repository.LaneRepository;
import com.example.transporterlanes.repository.TransporterRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Starts the real application on a random port (H2 test profile) and checks, over real HTTP, that Swagger UI is
 * served, that the OpenAPI document describes both endpoints with the intended status codes, and that the documented
 * examples are what the API actually returns.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OpenApiDocumentationTest {

    private static final String INPUT = "/api/v1/transporters/input";
    private static final String ASSIGNMENT = "/api/v1/transporters/assignment";

    @Autowired
    private TestRestTemplate http;
    @Autowired
    private ObjectMapper json;
    @Autowired
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

    private ResponseEntity<String> postJson(String path, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.postForEntity(path, new HttpEntity<>(body, headers), String.class);
    }

    // ---- Swagger UI and the OpenAPI document ------------------------------------------------------------------

    @Test
    void swaggerUiIsServedAtTheConfiguredPath() {
        ResponseEntity<String> page = http.getForEntity("/swagger-ui.html", String.class); // redirects to index.html

        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getBody()).contains("<title>Swagger UI</title>");

        ResponseEntity<String> initializer = http.getForEntity("/swagger-ui/swagger-initializer.js", String.class);
        assertThat(initializer.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(initializer.getBody()).contains("/v3/api-docs");
    }

    @Test
    void openApiDocumentDescribesBothEndpointsWithTheirOwnStatusCodes() throws Exception {
        ResponseEntity<String> docs = http.getForEntity("/v3/api-docs", String.class);
        assertThat(docs.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode api = json.readTree(docs.getBody());
        // Keep a copy for review (and for tooling) without needing a running PostgreSQL-backed application.
        Files.writeString(Path.of("target", "openapi.json"),
                json.writerWithDefaultPrettyPrinter().writeValueAsString(api));

        assertThat(api.at("/info/title").asText()).isEqualTo("Transporter Assignment on Lanes API");
        assertThat(api.at("/info/version").asText()).isEqualTo("v1");
        assertThat(fieldNames(api.at("/paths"))).containsExactlyInAnyOrder(INPUT, ASSIGNMENT);

        JsonNode input = api.at("/paths/~1api~1v1~1transporters~1input/post");
        JsonNode assignment = api.at("/paths/~1api~1v1~1transporters~1assignment/post");
        assertThat(input.at("/summary").asText()).isEqualTo("Submit input data");
        assertThat(assignment.at("/summary").asText()).isEqualTo("Get optimized assignment");
        assertThat(fieldNames(input.at("/responses"))).containsExactlyInAnyOrder("200", "400", "409", "500");
        assertThat(fieldNames(assignment.at("/responses")))
                .containsExactlyInAnyOrder("200", "400", "404", "422", "500");
        assertThat(input.at("/responses/200/content/application~1json/example/message").asText())
                .isEqualTo("Input data saved successfully.");
        assertThat(assignment.at("/responses/200/content/application~1json/example/totalCost").asLong())
                .isEqualTo(11_000);
        assertThat(fieldNames(input.at("/responses/400/content/application~1json/examples"))).hasSize(3);
        assertThat(fieldNames(assignment.at("/responses/422/content/application~1json/examples"))).hasSize(3);
        assertThat(input.at("/requestBody/content/application~1json/schema/$ref").asText())
                .endsWith("/InputRequest");
    }

    @Test
    void schemasCarryFieldDescriptionsAndValidationConstraints() throws Exception {
        JsonNode schemas = json.readTree(http.getForEntity("/v3/api-docs", String.class).getBody())
                .at("/components/schemas");

        JsonNode quote = schemas.at("/LaneQuoteRequest/properties/quote");
        assertThat(quote.at("/description").asText()).contains("whole rupees");
        assertThat(quote.at("/maximum").asLong()).isEqualTo(1_000_000_000_000L);
        assertThat(quote.at("/minimum").asLong()).isEqualTo(1);
        assertThat(schemas.at("/LaneRequest/properties/id/minimum").asLong()).isEqualTo(1);
        assertThat(schemas.at("/LaneRequest/properties/origin/maxLength").asInt()).isEqualTo(100);
        assertThat(schemas.at("/LaneRequest/properties/origin/description").asText()).contains("non-blank");
        assertThat(textValues(schemas.at("/LaneQuoteRequest/required"))).containsExactlyInAnyOrder("laneId", "quote");
        assertThat(textValues(schemas.at("/LaneRequest/required")))
                .containsExactlyInAnyOrder("id", "origin", "destination");
        assertThat(schemas.at("/AssignmentRequest/properties/maxTransporters/minimum").asInt()).isEqualTo(1);
        assertThat(schemas.at("/AssignmentRequest/properties/maxTransporters/description").asText())
                .contains("Upper limit");
        assertThat(schemas.at("/ErrorResponse/properties/errors/description").asText()).contains("Field-level");
    }

    // ---- The documented examples are real ---------------------------------------------------------------------

    @Test
    void successExamplesAreExactlyWhatTheApiReturns() throws Exception {
        ResponseEntity<String> input = postJson(INPUT, ApiExamples.INPUT_REQUEST);
        assertThat(input.getStatusCode()).isEqualTo(HttpStatus.OK);
        JSONAssert.assertEquals(ApiExamples.INPUT_SUCCESS, input.getBody(), JSONCompareMode.STRICT);

        ResponseEntity<String> assignment = postJson(ASSIGNMENT, ApiExamples.ASSIGNMENT_REQUEST);
        assertThat(assignment.getStatusCode()).isEqualTo(HttpStatus.OK);
        JSONAssert.assertEquals(ApiExamples.ASSIGNMENT_SUCCESS, assignment.getBody(), JSONCompareMode.STRICT);
    }

    @Test
    void noInputDataExampleIsReal() throws Exception {
        assertError(postJson(ASSIGNMENT, ApiExamples.ASSIGNMENT_REQUEST), HttpStatus.NOT_FOUND,
                ApiExamples.NO_INPUT_DATA_ERROR);
    }

    @Test
    void inputValidationExamplesAreReal() throws Exception {
        assertError(postJson(INPUT, """
                {"lanes": [{"id": 1, "origin": "", "destination": "Delhi"}],
                 "transporters": [{"id": 1, "name": "T1", "laneQuotes": [{"laneId": 1, "quote": 0}]}]}"""),
                HttpStatus.BAD_REQUEST, ApiExamples.BEAN_VALIDATION_ERROR);

        assertError(postJson(INPUT, """
                {"lanes": [{"id": 1, "origin": "A", "destination": "B"}, {"id": 2, "origin": "B", "destination": "C"},
                           {"id": 3, "origin": "C", "destination": "D"}],
                 "transporters": [
                   {"id": 1, "name": "T1", "laneQuotes": [{"laneId": 1, "quote": 10}, {"laneId": 99, "quote": 10}]},
                   {"id": 2, "name": "T2", "laneQuotes": [{"laneId": 2, "quote": 10}]}]}"""),
                HttpStatus.BAD_REQUEST, ApiExamples.CROSS_FIELD_ERROR);

        assertError(postJson(INPUT, """
                {"lanes": [{"id": 1, "origin": "A", "destination": "B"}],
                 "transporters": [{"id": 1, "name": "T1", "laneQuotes": [{"laneId": 1, "quote": 5000.75}]}]}"""),
                HttpStatus.BAD_REQUEST, ApiExamples.MALFORMED_BODY_ERROR);
    }

    @Test
    void assignmentErrorExamplesAreReal() throws Exception {
        assertError(postJson(ASSIGNMENT, "{\"maxTransporters\": 0}"), HttpStatus.BAD_REQUEST,
                ApiExamples.MAX_TRANSPORTERS_ERROR);

        postJson(INPUT, """
                {"lanes": [{"id": 1, "origin": "A", "destination": "B"}, {"id": 2, "origin": "B", "destination": "C"}],
                 "transporters": [{"id": 1, "name": "T1", "laneQuotes": [{"laneId": 1, "quote": 10}]},
                                  {"id": 2, "name": "T2", "laneQuotes": [{"laneId": 2, "quote": 10}]}]}""");
        assertError(postJson(ASSIGNMENT, "{\"maxTransporters\": 1}"), HttpStatus.UNPROCESSABLE_ENTITY,
                ApiExamples.INFEASIBLE_ERROR);
    }

    @Test
    void tooLargeExampleIsReal() throws Exception {
        // 100 lanes x 20 transporters with K = 20: (2^20 - 1) x 100 = 104,857,500 > 100,000,000 lane evaluations.
        List<Map<String, Object>> lanes = new ArrayList<>();
        List<Map<String, Object>> transporters = new ArrayList<>();
        for (int l = 1; l <= 100; l++) {
            lanes.add(Map.of("id", l, "origin", "O" + l, "destination", "D" + l));
        }
        for (int t = 1; t <= 20; t++) {
            List<Map<String, Object>> quotes = new ArrayList<>();
            for (int l = 1; l <= 100; l++) {
                quotes.add(Map.of("laneId", l, "quote", 1000 + t));
            }
            transporters.add(Map.of("id", t, "name", "T" + t, "laneQuotes", quotes));
        }
        String dataset = json.writeValueAsString(Map.of("lanes", lanes, "transporters", transporters));
        assertThat(postJson(INPUT, dataset).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertError(postJson(ASSIGNMENT, "{\"maxTransporters\": 20}"), HttpStatus.UNPROCESSABLE_ENTITY,
                ApiExamples.TOO_LARGE_ERROR);
    }

    @Test
    void overflowExampleMessageMatchesTheOptimizer() throws Exception {
        // Unreachable through the API (quotes are capped at 10^12), so check the message at the source.
        long huge = Long.MAX_VALUE / 2 + 1;
        var problem = new OptimizationProblem(List.of(1L, 2L), List.of(1L),
                List.of(new Quote(1, 1, huge), new Quote(2, 1, huge)), 1);
        String message = null;
        try {
            new ExactCombinationSolver().solve(problem);
        } catch (ArithmeticException e) {
            message = e.getMessage();
        }
        assertThat(message).isEqualTo(json.readTree(ApiExamples.OVERFLOW_ERROR).get("message").asText());
    }

    /** Status must match; body must match exactly, except that the order of {@code errors} entries is free. */
    private static void assertError(ResponseEntity<String> response, HttpStatus status, String example)
            throws Exception {
        assertThat(response.getStatusCode()).isEqualTo(status);
        JSONAssert.assertEquals(example, response.getBody(), JSONCompareMode.NON_EXTENSIBLE);
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static List<String> textValues(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.asText()));
        return values;
    }
}
