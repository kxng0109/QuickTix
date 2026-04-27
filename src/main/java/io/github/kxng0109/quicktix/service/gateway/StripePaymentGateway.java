package io.github.kxng0109.quicktix.service.gateway;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import io.github.kxng0109.quicktix.entity.Payment;
import io.github.kxng0109.quicktix.exception.PaymentFailedException;
import io.github.kxng0109.quicktix.service.gateway.dto.GatewayInitializationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Stripe-specific implementation of the {@link PaymentGateway}.
 * <p>
 * This service handles all direct HTTP communication with Stripe's API using
 * the modern v31+ {@link com.stripe.StripeClient}. It is responsible for creating
 * PaymentIntents and issuing refunds.
 */
@Service
@RequiredArgsConstructor
@Primary
@Profile({"!test & !mock-stress-test"})
@ConditionalOnProperty(name = "payment.gateway.provider", havingValue = "stripe", matchIfMissing = true)
@Slf4j
public class StripePaymentGateway implements PaymentGateway {

	private final StripeClient stripeClient;

	/**
	 * Creates a Stripe PaymentIntent for the specified payment.
	 * <p>
	 * Converts the payment amount to the lowest currency denomination (kobo in this case)
	 * and attaches internal QuickTix database IDs as metadata so the webhook
	 * can identify the transaction later.
	 *
	 * @param payment the internal payment record to be processed.
	 * @return a {@link GatewayInitializationResponse} containing the Stripe Client Secret
	 * as a String and the Stripe paymentIntent ID.
	 * @throws PaymentFailedException if the Stripe API rejects the request.
	 */
	@Override
	public GatewayInitializationResponse initializePayment(Payment payment) {
		try {
			long amountInKobo = payment.getAmount().multiply(BigDecimal.valueOf(100)).longValue();

			RequestOptions options = RequestOptions.builder()
			                                       .setIdempotencyKey(payment.getIdempotencyKey())
			                                       .build();

			PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
			                                                            .setAmount(amountInKobo)
			                                                            .setCurrency("ngn")
			                                                            //The paymentId and bookingId helps us later to change to state of a transaction or booking
			                                                            .putMetadata("paymentId",
			                                                                         String.valueOf(payment.getId())
			                                                            )
			                                                            .putMetadata("bookingId", String.valueOf(
					                                                            payment.getBooking().getId())
			                                                            )
			                                                            .build();

			//An object that tracks the entire lifecycle of a customer's checkout process
			PaymentIntent paymentIntent = stripeClient.v1().paymentIntents().create(params, options);
			log.info("Created Stripe PaymentIntent for Payment ID: {}", payment.getId());

			//It returns a client_secret that the frontend uses to complete a payment by rendering the credit card form
			//and returns the stripe transaction id for the transaction, to be saved in the database as transactionReference
			return GatewayInitializationResponse.builder()
			                                    .clientSecret(paymentIntent.getClientSecret())
			                                    .transactionId(paymentIntent.getId())
			                                    .build();
		} catch (StripeException e) {
			log.error("Stripe initialization failed for Payment ID: {}", payment.getId(), e);
			throw new PaymentFailedException("Failed to initialize payment with provider: " + e.getMessage());
		}
	}

	/**
	 * Submits a refund request to Stripe for a specific PaymentIntent.
	 *
	 * @param transactionReference the Stripe PaymentIntent ID.
	 * @return {@code true} if Stripe confirms the refund status is "succeeded".
	 * @throws PaymentFailedException if the refund fails.
	 */
	@Override
	public boolean refundTransaction(String transactionReference) {
		try {
			RefundCreateParams params = RefundCreateParams.builder()
			                                              .setPaymentIntent(transactionReference)
			                                              .build();

			Refund refund = stripeClient.v1().refunds().create(params);
			return "succeeded".equals(refund.getStatus());
		} catch (StripeException e) {
			log.error("Stripe refund failed for intent: {}", transactionReference, e);
			return false;
		}
	}
}
