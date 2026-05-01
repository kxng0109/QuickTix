package io.github.kxng0109.quicktix.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class PaymentGatewayUnavailableException extends RuntimeException {
	/**
	 * Exception indicating that the payment gateway is currently unavailable and cannot process transactions.
	 * <p>
	 * This exception is thrown when there are issues with connectivity or operational status of the payment
	 * gateway, preventing any transaction from being processed. It serves as a signal to the application's
	 * user interface or other layers that they should inform the user about the temporary unavailability
	 * and suggest alternative payment methods or retrying later.
	 * </p>
	 * <p>
	 * This exception is mapped to an HTTP 503 Service Unavailable status code, indicating that the service
	 * is currently unavailable due to a temporary condition which will likely be alleviated after some delay.
	 * </p>
	 *
	 * @param message The error message describing the cause of the unavailability.
	 * @param cause   The underlying cause of this exception (e.g., network issues, gateway downtime).
	 */
	public PaymentGatewayUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}
}
