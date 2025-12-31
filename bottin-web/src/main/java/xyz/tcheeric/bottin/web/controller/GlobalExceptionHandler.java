package xyz.tcheeric.bottin.web.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import xyz.tcheeric.bottin.core.exception.BottinException;
import xyz.tcheeric.bottin.core.exception.DomainNotFoundException;
import xyz.tcheeric.bottin.core.exception.DomainNotVerifiedException;
import xyz.tcheeric.bottin.core.exception.Nip05RecordExistsException;
import xyz.tcheeric.bottin.core.exception.Nip05RecordNotFoundException;
import xyz.tcheeric.bottin.web.dto.ErrorResponse;

import java.util.List;

/**
 * Global exception handler for REST API errors.
 * Provides consistent error responses across all endpoints.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldError)
                .toList();

        log.warn("validation_error path={} field_count={}", request.getRequestURI(), fieldErrors.size());

        return ResponseEntity.badRequest()
                .body(ErrorResponse.validation(
                        "Validation failed for request",
                        fieldErrors,
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        log.warn("message_not_readable path={} error={}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity.badRequest()
                .body(ErrorResponse.withSuggestion(
                        "INVALID_JSON",
                        "Request body is not valid JSON",
                        "Check the request body format and ensure it's valid JSON",
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {

        log.warn("missing_parameter path={} param={}", request.getRequestURI(), ex.getParameterName());

        return ResponseEntity.badRequest()
                .body(ErrorResponse.withSuggestion(
                        "MISSING_PARAMETER",
                        "Required parameter '" + ex.getParameterName() + "' is missing",
                        "Include the '" + ex.getParameterName() + "' parameter in your request",
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        log.warn("type_mismatch path={} param={} value={}",
                request.getRequestURI(), ex.getName(), ex.getValue());

        return ResponseEntity.badRequest()
                .body(ErrorResponse.withSuggestion(
                        "TYPE_MISMATCH",
                        "Invalid value for parameter '" + ex.getName() + "'",
                        "Provide a valid value of the expected type",
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {

        log.warn("method_not_allowed path={} method={}", request.getRequestURI(), ex.getMethod());

        String[] supportedMethods = ex.getSupportedMethods();
        String suggestion = supportedMethods != null && supportedMethods.length > 0
                ? "Use one of the supported methods: " + String.join(", ", supportedMethods)
                : "Check the API documentation for supported methods";

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ErrorResponse.withSuggestion(
                        "METHOD_NOT_ALLOWED",
                        "HTTP method " + ex.getMethod() + " is not supported for this endpoint",
                        suggestion,
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException ex, HttpServletRequest request) {

        log.debug("resource_not_found path={}", request.getRequestURI());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(
                        "NOT_FOUND",
                        "The requested resource was not found",
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(DomainNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleDomainNotFound(
            DomainNotFoundException ex, HttpServletRequest request) {

        log.warn("domain_not_found path={} message={}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.withSuggestion(
                        ex.getErrorCode(),
                        ex.getMessage(),
                        ex.getSuggestion(),
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(DomainNotVerifiedException.class)
    public ResponseEntity<ErrorResponse> handleDomainNotVerified(
            DomainNotVerifiedException ex, HttpServletRequest request) {

        log.warn("domain_not_verified path={} message={}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                .body(ErrorResponse.withSuggestion(
                        ex.getErrorCode(),
                        ex.getMessage(),
                        ex.getSuggestion(),
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(Nip05RecordNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRecordNotFound(
            Nip05RecordNotFoundException ex, HttpServletRequest request) {

        log.warn("nip05_record_not_found path={} message={}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.withSuggestion(
                        ex.getErrorCode(),
                        ex.getMessage(),
                        ex.getSuggestion(),
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(Nip05RecordExistsException.class)
    public ResponseEntity<ErrorResponse> handleRecordExists(
            Nip05RecordExistsException ex, HttpServletRequest request) {

        log.warn("nip05_record_exists path={} message={}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.withSuggestion(
                        ex.getErrorCode(),
                        ex.getMessage(),
                        ex.getSuggestion(),
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(BottinException.class)
    public ResponseEntity<ErrorResponse> handleBottinException(
            BottinException ex, HttpServletRequest request) {

        log.warn("bottin_exception path={} error_code={} message={}",
                request.getRequestURI(), ex.getErrorCode(), ex.getMessage());

        HttpStatus status = ex.isRetryable() ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_REQUEST;

        return ResponseEntity.status(status)
                .body(ErrorResponse.withSuggestion(
                        ex.getErrorCode(),
                        ex.getMessage(),
                        ex.getSuggestion(),
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {

        log.warn("illegal_argument path={} message={}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(
                        "INVALID_ARGUMENT",
                        ex.getMessage(),
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {

        log.error("unexpected_error path={} error={}", request.getRequestURI(), ex.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.withSuggestion(
                        "INTERNAL_ERROR",
                        "An unexpected error occurred",
                        "Please try again later or contact support if the problem persists",
                        request.getRequestURI()
                ));
    }

    private ErrorResponse.FieldError toFieldError(FieldError fieldError) {
        return ErrorResponse.FieldError.builder()
                .field(fieldError.getField())
                .message(fieldError.getDefaultMessage())
                .rejectedValue(fieldError.getRejectedValue())
                .build();
    }
}
