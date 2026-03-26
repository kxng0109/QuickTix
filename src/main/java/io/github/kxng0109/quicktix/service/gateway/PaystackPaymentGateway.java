package io.github.kxng0109.quicktix.service.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.kxng0109.quicktix.entity.Payment;
import io.github.kxng0109.quicktix.exception.PaymentFailedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Paystack-specific implementation of the {@link PaymentGateway}.
 * <p>
 * This service handles direct HTTP communication with Paystack's REST API using
 * Spring's {@link org.springframework.web.client.RestClient}. It is conditionally
 * loaded only when the application property {@code payment.gateway.provider} is
 * set to {@code paystack}.
 */
@Service
@Profile("!test")
@ConditionalOnProperty(name = "payment.gateway.provider", havingValue = "paystack")
@Slf4j
public class PaystackPaymentGateway implements PaymentGateway {

	private final RestClient restClient;

	public PaystackPaymentGateway(@Value("${paystack.secret.key}") String secretKey) {
		this.restClient = RestClient.builder()
		                            .baseUrl("https://api.paystack.co")
		                            .defaultHeader("Authorization", "Bearer " + secretKey)
		                            .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
		                            .build();
	}

	/**
	 * Initializes a Paystack payment session.
	 * <p>
	 * Converts the booking amount to Kobo (the lowest currency denomination) and
	 * makes a server-to-server POST request to Paystack's initialization endpoint.
	 * It embeds the internal QuickTix {@code paymentId} inside the metadata payload
	 * so it can be extracted later when the webhook fires.
	 *
	 * @param payment the internal payment record to be processed.
	 * @return the Paystack authorization URL, which the frontend will use to redirect the user.
	 * @throws PaymentFailedException if the HTTP request fails or Paystack returns an error.
	 */
	@Override
	public String initializePayment(Payment payment) {
		try {
			long amountInKobo = payment.getAmount().multiply(BigDecimal.valueOf(100)).longValue();
			String userEmail = payment.getBooking().getUser().getEmail();

			Map<String, Object> body = Map.of(
					"email", userEmail,
					"amount", amountInKobo,
					"currency", "NGN",
					"metadata", Map.of("paymentId", payment.getId())
			);

			JsonNode response = restClient.post()
			                              .uri("/transaction/initialize")
			                              .body(body)
			                              .retrieve()
			                              .body(JsonNode.class);

			if (response != null && response.get("status").asBoolean()) {
				log.info("Successfully initialized Paystack transaction for Payment ID: {}", payment.getId());
				// Return the authorization URL. The frontend will redirect the user to this link.
				return response.get("data").get("authorization_url").asText();
			} else {
				throw new PaymentFailedException("Paystack returned an error during initialization.");
			}
		} catch (Exception e) {
			log.error("Paystack initialization failed for Payment ID: {}", payment.getId(), e);
			throw new PaymentFailedException("Failed to initialize payment with Paystack: " + e.getMessage());
		}
	}

	/**
	 * Submits a refund request to Paystack for a specific transaction.
	 *
	 * @param transactionReference the Paystack transaction reference string.
	 * @return {@code true} if Paystack confirms the refund successfully, {@code false} otherwise.
	 */
	@Override
	public boolean refundTransaction(String transactionReference) {
		try {
			Map<String, Object> body = Map.of(
					"transaction", transactionReference,
					"currency", "NGN"
			);

			JsonNode response = restClient.post()
			                              .uri("/refund")
			                              .body(body)
			                              .retrieve()
			                              .body(JsonNode.class);

			return response != null && response.get("status").asBoolean();
		} catch (Exception e) {
			log.error("Paystack refund failed for reference: {}", transactionReference, e);
			return false;
		}
	}
}
