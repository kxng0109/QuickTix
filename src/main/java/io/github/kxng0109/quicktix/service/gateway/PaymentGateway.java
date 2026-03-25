package io.github.kxng0109.quicktix.service.gateway;

import io.github.kxng0109.quicktix.entity.Payment;

public interface PaymentGateway {
	/**
	 * Initializes a payment session with the external payment provider.
	 * <p>
	 * For providers like Stripe, this creates a PaymentIntent and returns the
	 * associated Client Secret, which the frontend requires to securely collect
	 * payment details directly from the user.
	 *
	 * @param payment the {@link Payment} entity containing the amount and metadata.
	 * @return a secure client token (e.g., Stripe Client Secret) as a String.
	 * @throws io.github.kxng0109.quicktix.exception.PaymentFailedException if the provider fails to initialize.
	 */
	String initializePayment(Payment payment);

	/**
	 * Attempts to reverse a completed transaction on the provider's side.
	 *
	 * @param transactionReference the unique transaction ID provided by the gateway
	 * (e.g., a Stripe PaymentIntent ID).
	 * @return {@code true} if the refund was successfully processed, {@code false} otherwise.
	 */
	boolean refundTransaction(String transactionReference);
}
