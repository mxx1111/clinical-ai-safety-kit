package io.github.mxx1111.medagentguard.web;

import io.github.mxx1111.medagentguard.fhir.FhirRequestException;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(FhirRequestException.class)
    public ResponseEntity<FhirApiError> handleFhirRequest(FhirRequestException exception) {
        return ResponseEntity.badRequest().body(new FhirApiError(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                exception.code(),
                exception.getMessage(),
                exception.evidence()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        List<String> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return ResponseEntity.badRequest().body(new ApiError(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Validation failed",
                errors));
    }

    public record ApiError(Instant timestamp, int status, String message, List<String> details) {
    }

    public record FhirApiError(
            Instant timestamp,
            int status,
            String code,
            String message,
            String evidence) {
    }
}
