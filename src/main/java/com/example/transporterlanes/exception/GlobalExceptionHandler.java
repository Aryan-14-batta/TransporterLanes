package com.example.transporterlanes.exception;

import com.example.transporterlanes.dto.ErrorResponse;
import com.example.transporterlanes.dto.ErrorResponse.FieldError;
import com.example.transporterlanes.optimizer.NoFeasibleAssignmentException;
import com.example.transporterlanes.optimizer.ProblemTooLargeException;
import com.fasterxml.jackson.databind.JsonMappingException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every error into the same JSON shape: {@code {"status":"error","message":...,"errors":[...]}}.
 *
 * <p>Extending {@link ResponseEntityExceptionHandler} keeps Spring MVC's own status codes (405, 415, 404 for unknown
 * paths, ...) while replacing its default body with ours.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ---- 400 -------------------------------------------------------------------------------------------------

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(ErrorResponse.of("Request validation failed.", errors));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // Name the offending field when Jackson knows it, without echoing Jackson's internal class names.
        if (ex.getCause() instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()) {
            String field = jsonPath(mapping.getPath());
            return ResponseEntity.badRequest().body(ErrorResponse.of("Malformed request body.",
                    List.of(new FieldError(field, "Invalid value or type"))));
        }
        return ResponseEntity.badRequest().body(ErrorResponse.of("Malformed request body."));
    }

    @ExceptionHandler(InvalidInputException.class)
    public ResponseEntity<ErrorResponse> handleInvalidInput(InvalidInputException ex) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(ex.getMessage(), ex.getErrors()));
    }

    // ---- 404 -------------------------------------------------------------------------------------------------

    @ExceptionHandler(NoInputDataException.class)
    public ResponseEntity<ErrorResponse> handleNoInputData(NoInputDataException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(ex.getMessage()));
    }

    // ---- 409 -------------------------------------------------------------------------------------------------

    /**
     * Only the dataset replacement writes. It validates the request first and empties the tables in its own
     * transaction, so an integrity or locking failure there means another replacement ran at the same time.
     */
    @ExceptionHandler({DataIntegrityViolationException.class, ConcurrencyFailureException.class})
    public ResponseEntity<ErrorResponse> handleConcurrentModification(RuntimeException ex) {
        log.warn("Dataset replacement conflicted with a concurrent request", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(
                "The dataset was modified by a concurrent request. Please retry."));
    }

    // ---- 422 -------------------------------------------------------------------------------------------------

    @ExceptionHandler({NoFeasibleAssignmentException.class, ProblemTooLargeException.class,
            UnrepresentableTotalCostException.class})
    public ResponseEntity<ErrorResponse> handleUnprocessable(RuntimeException ex) {
        return ResponseEntity.unprocessableEntity().body(ErrorResponse.of(ex.getMessage()));
    }

    // ---- 500 -------------------------------------------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.internalServerError().body(ErrorResponse.of("An unexpected error occurred."));
    }

    // ---- Spring MVC's own exceptions (405, 415, ...): keep the status, use our body ---------------------------

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String reason = status instanceof HttpStatus known ? known.getReasonPhrase() : "Request failed";
        return ResponseEntity.status(status).headers(headers).body(ErrorResponse.of(reason + "."));
    }

    private static String jsonPath(List<JsonMappingException.Reference> path) {
        StringBuilder field = new StringBuilder();
        for (JsonMappingException.Reference reference : path) {
            if (reference.getIndex() >= 0) {
                field.append('[').append(reference.getIndex()).append(']');
            } else {
                if (!field.isEmpty()) {
                    field.append('.');
                }
                field.append(reference.getFieldName());
            }
        }
        return field.toString();
    }
}
