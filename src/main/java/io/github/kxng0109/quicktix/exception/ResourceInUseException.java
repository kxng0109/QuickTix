package io.github.kxng0109.quicktix.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when attempting to delete or permanently modify a domain entity
 * that is currently referenced by other critical records.
 * <p>
 * Annotated with {@code @ResponseStatus(HttpStatus.CONFLICT)} to translate into a 409 Conflict response.
 * Used primarily to enforce referential integrity, such as preventing the deletion of a Venue
 * that currently has scheduled Events, or a User that has financial Booking records.
 * </p>
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class ResourceInUseException extends RuntimeException {
    public ResourceInUseException(String message) {
        super(message);
    }
}
