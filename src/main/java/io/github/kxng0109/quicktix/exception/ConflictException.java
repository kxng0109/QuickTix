package io.github.kxng0109.quicktix.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception indicating that a conflict occurred while processing a request.
 * <p>
 * This exception serves as a base class for scenarios where an operation
 * cannot be completed due to a conflicting state, such as attempting to
 * create or update a resource that violates unique constraints or other
 * integrity rules. It is mapped to an HTTP 409 Conflict status code.
 * </p>
 * <p>
 * Subclasses can provide more specific error contexts for domain-specific
 * conflicts, such as duplicate user registrations or conflicting updates.
 * </p>
 *
 * <p><strong>Example usage:</strong></p>
 * <pre>
 * &#64;ResponseStatus(HttpStatus.CONFLICT)
 * public class UserExistsException extends ConflictException {
 *     public UserExistsException() {
 *         super("User with this email exists.");
 *     }
 *
 *     public UserExistsException(String message) {
 *         super(message);
 *     }
 * }
 * </pre>
 *
 * <p><strong>Error Handling:</strong></p>
 * This exception is intended to work with a centralized exception handler
 * (e.g., {@link GlobalExceptionHandler}) to construct a standardized error
 * response. For instance, it is transformed into an {@code ErrorResponse} object
 * with HTTP 409 status in the application's response pipeline.
 *
 * @see GlobalExceptionHandler
 * @see UserExistsException
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class ConflictException extends RuntimeException{
	public ConflictException(){
		super("Conflict occurred while handling this request.");
	}

	public ConflictException(String message){
		super(message);
	}
}
