package io.github.kxng0109.quicktix.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a requested business operation violates domain rules or state machine constraints.
 * <p>
 * Annotated with {@code @ResponseStatus(HttpStatus.BAD_REQUEST)} to automatically translate
 * into a 400 Bad Request HTTP response. Examples include attempting to cancel an already
 * completed event or trying to refund a payment that hasn't succeeded.
 * </p>
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidOperationException extends RuntimeException {
    public InvalidOperationException(String message) {
        super(message);
    }
}
