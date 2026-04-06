package io.github.kxng0109.quicktix.exception;

/**
 * Exception thrown when communication with an external payment gateway (e.g., Stripe, Paystack)
 * results in a failure or rejection.
 * <p>
 * This exception acts as a generic wrapper around vendor-specific errors, allowing the internal
 * services to handle transaction failures cleanly without being tightly coupled to a single provider's SDK.
 * </p>
 */
public class PaymentFailedException extends RuntimeException {
    public PaymentFailedException(String message) {
        super(message);
    }
}
