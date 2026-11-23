package com.example.transporterlanes.controller;

import com.example.transporterlanes.dto.AssignmentRequest;
import com.example.transporterlanes.dto.AssignmentResponse;
import com.example.transporterlanes.dto.ErrorResponse;
import com.example.transporterlanes.dto.InputRequest;
import com.example.transporterlanes.dto.InputResponse;
import com.example.transporterlanes.service.AssignmentService;
import com.example.transporterlanes.service.TransporterInputService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/transporters",
        consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Transporter assignment", description = "Submit lane/transporter data and compute optimal assignments")
public class TransporterController {

    private final TransporterInputService inputService;
    private final AssignmentService assignmentService;

    public TransporterController(TransporterInputService inputService, AssignmentService assignmentService) {
        this.inputService = inputService;
        this.assignmentService = assignmentService;
    }

    /** Replaces the stored dataset. 200 rather than 201: no new addressable resource is created. */
    @PostMapping("/input")
    @Operation(
            summary = "Submit input data",
            description = """
                    Validates the complete request, then replaces the stored dataset (all lanes, transporters and \
                    quotes) in one transaction. If anything fails, the previous dataset is kept unchanged.

                    Rules beyond the per-field constraints: lane ids and transporter ids are unique; every \
                    `laneQuotes[].laneId` refers to a lane in this request; a transporter quotes each lane at most \
                    once; every lane is quoted by at least one transporter. A transporter may have no quotes.""",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(
                    examples = @ExampleObject(name = "Three lanes, three transporters",
                            value = ApiExamples.INPUT_REQUEST))))
    @ApiResponse(responseCode = "200", description = "Dataset stored, replacing any previous one",
            content = @Content(schema = @Schema(implementation = InputResponse.class),
                    examples = @ExampleObject(value = ApiExamples.INPUT_SUCCESS)))
    @ApiResponse(responseCode = "400", description = "Invalid request; nothing was changed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                    @ExampleObject(name = "Field validation", value = ApiExamples.BEAN_VALIDATION_ERROR),
                    @ExampleObject(name = "Inconsistent data", value = ApiExamples.CROSS_FIELD_ERROR),
                    @ExampleObject(name = "Malformed JSON or wrong type (e.g. a decimal quote)",
                            value = ApiExamples.MALFORMED_BODY_ERROR)}))
    @ApiResponse(responseCode = "409", description = "Conflicted with a concurrent dataset replacement; retry",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = ApiExamples.CONFLICT_ERROR)))
    @ApiResponse(responseCode = "500", description = "Unexpected error; the previous dataset is kept",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = ApiExamples.UNEXPECTED_ERROR)))
    public InputResponse submitInput(@Valid @RequestBody InputRequest request) {
        inputService.replaceDataset(request);
        return InputResponse.success();
    }

    @PostMapping("/assignment")
    @Operation(
            summary = "Get optimized assignment",
            description = """
                    Computes, over the stored dataset, the minimum-cost assignment of exactly one transporter per \
                    lane using at most `maxTransporters` distinct transporters (a value above the number of \
                    transporters is treated as that number). Fewer transporters are used when that is not more \
                    expensive. The result is exact, not a heuristic.

                    Ties: equal total cost → fewer transporters → lexicographically smaller sorted transporter ids. \
                    Within the chosen transporters, each lane goes to the lowest quote; equal quotes go to the \
                    lower transporter id. `assignments` is ordered by lane id; `selectedTransporters` ascending.""",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(
                    examples = @ExampleObject(name = "At most two transporters",
                            value = ApiExamples.ASSIGNMENT_REQUEST))))
    @ApiResponse(responseCode = "200",
            description = "Optimal assignment (the example is for the example input above)",
            content = @Content(schema = @Schema(implementation = AssignmentResponse.class),
                    examples = @ExampleObject(value = ApiExamples.ASSIGNMENT_SUCCESS)))
    @ApiResponse(responseCode = "400", description = "Missing, non-integer or < 1 maxTransporters",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = ApiExamples.MAX_TRANSPORTERS_ERROR)))
    @ApiResponse(responseCode = "404", description = "No input data has been submitted yet",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = ApiExamples.NO_INPUT_DATA_ERROR)))
    @ApiResponse(responseCode = "422", description = "The stored data cannot be optimized as requested",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                    @ExampleObject(name = "No combination within the limit covers every lane",
                            value = ApiExamples.INFEASIBLE_ERROR),
                    @ExampleObject(name = "Problem exceeds the configured size limits",
                            value = ApiExamples.TOO_LARGE_ERROR),
                    @ExampleObject(name = "Every feasible total exceeds the representable range",
                            value = ApiExamples.OVERFLOW_ERROR)}))
    @ApiResponse(responseCode = "500", description = "Unexpected error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = ApiExamples.UNEXPECTED_ERROR)))
    public AssignmentResponse assign(@Valid @RequestBody AssignmentRequest request) {
        return assignmentService.assign(request.maxTransporters());
    }
}
