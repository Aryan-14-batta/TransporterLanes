package com.example.transporterlanes.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.transporterlanes.dto.AssignmentResponse;
import com.example.transporterlanes.dto.ErrorResponse.FieldError;
import com.example.transporterlanes.dto.InputRequest;
import com.example.transporterlanes.dto.LaneAssignmentResponse;
import com.example.transporterlanes.exception.InvalidInputException;
import com.example.transporterlanes.exception.NoInputDataException;
import com.example.transporterlanes.exception.UnrepresentableTotalCostException;
import com.example.transporterlanes.optimizer.ExactCombinationSolver;
import com.example.transporterlanes.optimizer.NoFeasibleAssignmentException;
import com.example.transporterlanes.optimizer.OptimizationProblem;
import com.example.transporterlanes.optimizer.ProblemTooLargeException;
import com.example.transporterlanes.optimizer.Quote;
import com.example.transporterlanes.service.AssignmentService;
import com.example.transporterlanes.service.TransporterInputService;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(TransporterController.class)
class TransporterControllerTest {

    private static final String INPUT = "/api/v1/transporters/input";
    private static final String ASSIGNMENT = "/api/v1/transporters/assignment";

    private static final String VALID_INPUT = """
            {
              "lanes": [{"id": 1, "origin": "Mumbai", "destination": "Delhi"}],
              "transporters": [{"id": 1, "name": "Transporter T1", "laneQuotes": [{"laneId": 1, "quote": 5000}]}]
            }""";

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private TransporterInputService inputService;
    @MockitoBean
    private AssignmentService assignmentService;

    private ResultActions postJson(String path, String body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Nested
    class Input {

        @Test
        void returnsTheSpecifiedSuccessBody() throws Exception {
            postJson(INPUT, VALID_INPUT)
                    .andExpect(status().isOk())
                    .andExpect(content().json("""
                            {"status": "success", "message": "Input data saved successfully."}""", true));

            ArgumentCaptor<InputRequest> request = ArgumentCaptor.forClass(InputRequest.class);
            verify(inputService).replaceDataset(request.capture());
            org.assertj.core.api.Assertions.assertThat(request.getValue().transporters().get(0).laneQuotes().get(0).quote())
                    .isEqualTo(5000L);
        }

        @Test
        void missingTopLevelListsAreReported() throws Exception {
            postJson(INPUT, "{}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.message").value("Request validation failed."))
                    .andExpect(jsonPath("$.errors[*].field").value(
                            org.hamcrest.Matchers.containsInAnyOrder("lanes", "transporters")));
            verifyNoInteractions(inputService);
        }

        @Test
        void nestedFieldsAreValidatedWithFullPaths() throws Exception {
            postJson(INPUT, """
                    {
                      "lanes": [{"id": 0, "origin": " ", "destination": "Delhi"}],
                      "transporters": [{"id": 1, "name": "", "laneQuotes": [{"laneId": null, "quote": 0}]}]
                    }""")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[*].field").value(org.hamcrest.Matchers.containsInAnyOrder(
                            "lanes[0].id", "lanes[0].origin", "transporters[0].name",
                            "transporters[0].laneQuotes[0].laneId", "transporters[0].laneQuotes[0].quote")));
            verifyNoInteractions(inputService);
        }

        @Test
        void nullListElementsAndMissingQuoteListAreRejected() throws Exception {
            postJson(INPUT, """
                    {"lanes": [null], "transporters": [{"id": 1, "name": "T1"}]}""")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[*].field").value(org.hamcrest.Matchers.containsInAnyOrder(
                            "lanes[0]", "transporters[0].laneQuotes")));
        }

        @Test
        void quoteAboveOneLakhCroreIsRejected() throws Exception {
            postJson(INPUT, VALID_INPUT.replace("5000", "1000000000001"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("transporters[0].laneQuotes[0].quote"));
        }

        @Test
        void quoteOfExactlyOneLakhCroreIsAccepted() throws Exception {
            postJson(INPUT, VALID_INPUT.replace("5000", "1000000000000")).andExpect(status().isOk());
        }

        @Test
        void decimalQuoteIsRejectedNotTruncated() throws Exception {
            postJson(INPUT, VALID_INPUT.replace("5000", "5000.75"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Malformed request body."))
                    .andExpect(jsonPath("$.errors[0].field").value("transporters[0].laneQuotes[0].quote"));
            verifyNoInteractions(inputService);
        }

        @Test
        void invalidJsonSyntaxIsRejectedWithoutAFieldName() throws Exception {
            postJson(INPUT, "{ not json }")
                    .andExpect(status().isBadRequest())
                    .andExpect(content().json("""
                            {"status": "error", "message": "Malformed request body.", "errors": []}""", true));
            verifyNoInteractions(inputService);
        }

        @Test
        void truncatedBodyIsRejectedAndNamesTheFieldBeingRead() throws Exception {
            postJson(INPUT, "{\"lanes\": [")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Malformed request body."))
                    .andExpect(jsonPath("$.errors[0].field").value("lanes"));
            verifyNoInteractions(inputService);
        }

        @Test
        void crossFieldErrorsFromTheServiceAreReturnedAs400() throws Exception {
            doThrow(new InvalidInputException(List.of(new FieldError("lanes[1].id", "Duplicate lane id 1"))))
                    .when(inputService).replaceDataset(any());

            postJson(INPUT, VALID_INPUT)
                    .andExpect(status().isBadRequest())
                    .andExpect(content().json("""
                            {"status": "error", "message": "Input data is invalid.",
                             "errors": [{"field": "lanes[1].id", "message": "Duplicate lane id 1"}]}""", true));
        }

        @Test
        void concurrentIntegrityConflictIsReturnedAs409() throws Exception {
            doThrow(new DataIntegrityViolationException("duplicate key")).when(inputService).replaceDataset(any());

            postJson(INPUT, VALID_INPUT)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(
                            "The dataset was modified by a concurrent request. Please retry."));
        }

        @Test
        void lockingConflictIsReturnedAs409() throws Exception {
            doThrow(new CannotAcquireLockException("lock timeout")).when(inputService).replaceDataset(any());

            postJson(INPUT, VALID_INPUT).andExpect(status().isConflict());
        }
    }

    @Nested
    class Assignment {

        @Test
        void returnsTheSpecifiedResponseShape() throws Exception {
            when(assignmentService.assign(3)).thenReturn(AssignmentResponse.success(29_000,
                    List.of(new LaneAssignmentResponse(1, 1), new LaneAssignmentResponse(2, 3)), List.of(1L, 3L)));

            postJson(ASSIGNMENT, "{\"maxTransporters\": 3}")
                    .andExpect(status().isOk())
                    .andExpect(content().json("""
                            {"status": "success", "totalCost": 29000,
                             "assignments": [{"laneId": 1, "transporterId": 1}, {"laneId": 2, "transporterId": 3}],
                             "selectedTransporters": [1, 3]}""", true));
        }

        @Test
        void maxTransportersMustBePresentAndAtLeastOne() throws Exception {
            postJson(ASSIGNMENT, "{}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("maxTransporters"));
            postJson(ASSIGNMENT, "{\"maxTransporters\": 0}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("maxTransporters"));
            verifyNoInteractions(assignmentService);
        }

        @Test
        void noInputDataIsReturnedAs404() throws Exception {
            when(assignmentService.assign(anyInt())).thenThrow(new NoInputDataException());

            postJson(ASSIGNMENT, "{\"maxTransporters\": 1}")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("No input data found.")));
        }

        @Test
        void infeasibleAssignmentIsReturnedAs422() throws Exception {
            when(assignmentService.assign(anyInt())).thenThrow(new NoFeasibleAssignmentException(1, List.of(),
                    "No combination of at most 1 transporter(s) covers all 2 lanes."));

            postJson(ASSIGNMENT, "{\"maxTransporters\": 1}")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.message").value(
                            "No combination of at most 1 transporter(s) covers all 2 lanes."));
        }

        @Test
        void oversizedProblemIsReturnedAs422() throws Exception {
            ProblemTooLargeException tooLarge = tooLargeException();
            when(assignmentService.assign(anyInt())).thenThrow(tooLarge);

            postJson(ASSIGNMENT, "{\"maxTransporters\": 2}")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.message").value(tooLarge.getMessage()));
        }

        @Test
        void unrepresentableTotalCostIsReturnedAs422() throws Exception {
            when(assignmentService.assign(anyInt())).thenThrow(
                    new UnrepresentableTotalCostException(new ArithmeticException("Every feasible combination ...")));

            postJson(ASSIGNMENT, "{\"maxTransporters\": 1}")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.message").value("Every feasible combination ..."));
        }

        @Test
        void unexpectedErrorsAreReturnedAs500WithoutInternalDetails() throws Exception {
            when(assignmentService.assign(anyInt())).thenThrow(new IllegalStateException("secret internal detail"));

            postJson(ASSIGNMENT, "{\"maxTransporters\": 1}")
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().json("""
                            {"status": "error", "message": "An unexpected error occurred.", "errors": []}""", true));
        }

        /** Real instance from the optimizer (its constructor is not public): 2 lanes x 2 transporters, tiny limit. */
        private static ProblemTooLargeException tooLargeException() {
            var problem = new OptimizationProblem(List.of(1L, 2L), List.of(1L, 2L),
                    List.of(new Quote(1, 1, 1), new Quote(2, 2, 1)), 2);
            try {
                new ExactCombinationSolver(1, 100).solve(problem);
            } catch (ProblemTooLargeException e) {
                return e;
            }
            throw new AssertionError("expected ProblemTooLargeException");
        }
    }

    @Nested
    class HttpProtocol {

        @Test
        void wrongMethodKeepsStatusAndUsesOurErrorBody() throws Exception {
            mvc.perform(get(ASSIGNMENT))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.message").value("Method Not Allowed."));
        }

        @Test
        void wrongContentTypeKeepsStatusAndUsesOurErrorBody() throws Exception {
            mvc.perform(post(INPUT).contentType(MediaType.TEXT_PLAIN).content("hello"))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(jsonPath("$.status").value("error"));
        }
    }
}
