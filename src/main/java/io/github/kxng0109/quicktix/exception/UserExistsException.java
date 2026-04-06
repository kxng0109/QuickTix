package io.github.kxng0109.quicktix.exception;

/**
 * Exception thrown during registration or profile updates when a requested unique identifier
 * (such as an email address) is already claimed by another account.
 * <p>
 * Prevents unique constraint violations in the database and signals to the client that
 * they must provide alternative credentials or initiate a password reset.
 * </p>
 */
public class UserExistsException extends RuntimeException {
    public UserExistsException(){
        super("User with this email exists.");
    }

    public UserExistsException(String message) {
        super(message);
    }
}
