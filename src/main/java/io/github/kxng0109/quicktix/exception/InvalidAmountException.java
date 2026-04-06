package io.github.kxng0109.quicktix.exception;

/**
 * Exception thrown when a payment or transactional amount does not match the expected value.
 * <p>
 * Typically triggered during the checkout phase if a client submits a payment request
 * where the requested charge differs from the securely calculated backend booking total,
 * indicating a potential data manipulation attempt or stale state.
 * </p>
 */
public class InvalidAmountException extends RuntimeException {
    public InvalidAmountException(String message) {
        super(message);
    }
}
