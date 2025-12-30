package xyz.tcheeric.bottin.core.exception;

import lombok.Getter;

/**
 * Base exception for all Bottin-specific errors.
 * Provides structured error information including error code, retry flag, and actionable suggestion.
 */
@Getter
public abstract class BottinException extends RuntimeException {

    private final String errorCode;
    private final boolean retryable;
    private final String suggestion;

    protected BottinException(String errorCode, boolean retryable, String message, String suggestion) {
        super(message);
        this.errorCode = errorCode;
        this.retryable = retryable;
        this.suggestion = suggestion;
    }

    protected BottinException(String errorCode, boolean retryable, String message, String suggestion, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.retryable = retryable;
        this.suggestion = suggestion;
    }

    @Override
    public String toString() {
        return String.format("%s[errorCode=%s, retryable=%s, message=%s, suggestion=%s]",
                getClass().getSimpleName(), errorCode, retryable, getMessage(), suggestion);
    }
}
