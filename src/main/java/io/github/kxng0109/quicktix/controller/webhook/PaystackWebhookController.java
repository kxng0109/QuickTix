package io.github.kxng0109.quicktix.controller.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kxng0109.quicktix.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * REST Controller responsible for listening to server-to-server events from Paystack.
 * <p>
 * This endpoint is whitelisted in the Spring Security configuration. Unlike Stripe,
 * which provides an SDK for cryptography, this controller manually computes an
 * HMAC SHA-512 hash of the incoming payload to verify the {@code x-paystack-signature} header.
 */
@RestController
@RequestMapping("/api/v1/webhooks/paystack")
@Slf4j
@RequiredArgsConstructor
public class PaystackWebhookController {

	private final PaymentService paymentService;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Value("${paystack.secret.key}")
	private String secretKey;

	/**
	 * Receives, validates, and processes webhook events sent by Paystack.
	 * <p>
	 * The payload is captured as a raw String to ensure the byte order remains exact
	 * for accurate cryptographic hashing. If the signature matches and the event type
	 * is {@code charge.success}, the method extracts the custom metadata and routes
	 * the transaction to the service layer for confirmation.
	 *
	 * @param payload   the raw JSON string sent by Paystack.
	 * @param sigHeader the {@code x-paystack-signature} header containing the HMAC hash.
	 * @return a 200 OK Response Entity to acknowledge receipt, or a 400 Bad Request
	 * if the signature verification fails.
	 */
	@PostMapping
	public ResponseEntity<String> handlePaystackEvent(
			@RequestBody String payload,
			@RequestHeader(value = "x-paystack-signature", required = false) String sigHeader
	) {
		/*
		 * Since your webhook URL is publicly available, you need to verify that events originate from Paystack and not a bad actor. There are two ways to ensure events to your webhook URL are from Paystack:
		 * i. Signature validation
		 * ii. IP whitelisting
		 * We are using the first one. Now unlike stripe that handles the signature validation or verification,
		 * for paystack we'll need to create a HMAC SHA512 of our secret key and compare it to the value of the x header
		 * signature that was sent by paystack, since that is also what paystack used as the value of the x header signature
		 */

		if (sigHeader == null || !isValidSignature(payload, sigHeader)) {
			log.error("Invalid Paystack webhook signature.");
			return new ResponseEntity<>("Invalid signature.", HttpStatus.BAD_REQUEST);
		}

		try {
			JsonNode eventNode = objectMapper.readTree(payload);
			String eventType = eventNode.get("event").asText();

			if ("charge.success".equals(eventType)) {
				JsonNode dataNode = eventNode.get("data");
				String reference = dataNode.get("reference").asText();

				JsonNode metadataNode = dataNode.get("metadata");
				if (metadataNode != null && metadataNode.has("paymentId")) {
					Long paymentId = metadataNode.get("paymentId").asLong();
					paymentService.handleSuccessfulWebhookPayment(paymentId, reference);
				} else {
					log.warn("Paystack success event received without paymentId in metadata. Reference: {}", reference);
				}
			}

			return ResponseEntity.ok("Success");
		} catch (Exception e) {
			log.error("Error parsing Paystack webhook payload", e);
			return new ResponseEntity<>("Invalid payload", HttpStatus.BAD_REQUEST);
		}
	}

	/**
	 * Manually computes the HMAC SHA-512 hash of the payload using the secret key
	 * and compares it against the signature provided by Paystack.
	 *
	 * @param payload   the raw webhook payload.
	 * @param sigHeader the expected signature from the request header.
	 * @return {@code true} if the computed hash matches the provided signature, {@code false} otherwise.
	 */
	private boolean isValidSignature(String payload, String sigHeader) {
		try {
			final String HMAC_SHA_512 = "HmacSHA512";
			byte[] secretKeyBytes = secretKey.getBytes(StandardCharsets.UTF_8);

			Mac mac = Mac.getInstance(HMAC_SHA_512);
			SecretKeySpec secretKeySpec = new SecretKeySpec(secretKeyBytes, HMAC_SHA_512);
			mac.init(secretKeySpec);
			byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

			// Convert byte array to hexadecimal string
			StringBuilder result = new StringBuilder();
			for (byte b : digest) {
				result.append(String.format("%02x", b));
			}

			return result.toString().toLowerCase().equals(sigHeader);
		} catch (Exception e) {
			log.error("Error computing HMAC SHA-512 signature", e);
			return false;
		}
	}
}
