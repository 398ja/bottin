package xyz.tcheeric.bottin.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Standard error response for API errors.
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    String errorCode;
    String message;
    String suggestion;
    boolean retryable;
    Instant timestamp;
    String path;
    List<FieldError> fieldErrors;
    Map<String, Object> details;

    /**
     * Field-level validation error.
     */
    @Value
    @Builder
    public static class FieldError {
        String field;
        String message;
        Object rejectedValue;
    }

    /**
     * Creates a simple error response.
     */
    public static ErrorResponse of(String errorCode, String message, String path) {
        return ErrorResponse.builder()
                .errorCode(errorCode)
                .message(message)
                .retryable(false)
                .timestamp(Instant.now())
                .path(path)
                .build();
    }

    /**
     * Creates an error response with a suggestion.
     */
    public static ErrorResponse withSuggestion(String errorCode, String message, String suggestion, String path) {
        return ErrorResponse.builder()
                .errorCode(errorCode)
                .message(message)
                .suggestion(suggestion)
                .retryable(false)
                .timestamp(Instant.now())
                .path(path)
                .build();
    }

    /**
     * Creates a validation error response.
     */
    public static ErrorResponse validation(String message, List<FieldError> fieldErrors, String path) {
        return ErrorResponse.builder()
                .errorCode("VALIDATION_ERROR")
                .message(message)
                .fieldErrors(fieldErrors)
                .retryable(false)
                .timestamp(Instant.now())
                .path(path)
                .build();
    }
}
