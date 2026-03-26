package io.github.kxng0109.quicktix.controller.webhook;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import io.github.kxng0109.quicktix.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Stripe calls it
 * REST Controller responsible for listening to server-to-server events from Stripe.
 * <p>
 * This endpoint is completely whitelisted in the Spring Security configuration.
 * It relies on cryptographic HMAC signature verification to ensure incoming
 * payloads genuinely originated from Stripe's servers.
 */
@RestController
@RequestMapping("/api/v1/webhooks/stripe")
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookController {

	private final PaymentService paymentService;

	@Value("${stripe.webhook.secret}")
	private String endpointSecret;

	/**
	 * Receives and validates webhook events sent by Stripe.
	 * <p>
	 * The payload must be read as a raw String to ensure the byte order remains
	 * perfectly intact for cryptographic signature verification. If the signature
	 * is valid and the event is a successful payment intent, it routes the data
	 * to the internal service layer.
	 *
	 * @param payload   the raw JSON string sent by Stripe.
	 * @param sigHeader the {@code Stripe-Signature} header containing the HMAC hash.
	 * @return a 200 OK Response Entity if successfully received (prevents Stripe from retrying),
	 * or a 400 Bad Request if the signature is invalid.
	 */
	@PostMapping
	public ResponseEntity<String> handleStripEvent(
			@RequestBody String payload,
			@RequestHeader("Stripe-Signature") String sigHeader
	) {
		Event event;

		try {
			//Because this endpoint is whitelisted, anyone on the internet can hit it.
			//To prevent hackers from faking a "payment successful" request, Stripe sends a cryptographic signature in the headers.
			//Hashes the raw payload using the endpointSecret and compares it to Stripe's header.
			event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
		} catch (SignatureVerificationException e) {
			log.error("Invalid Stripe signature", e);
			return new ResponseEntity<>("Invalid signature", HttpStatus.BAD_REQUEST);
		} catch (Exception e) {
			log.error("Error parsing Stripe webhook", e);
			return new ResponseEntity<>("Invalid payload", HttpStatus.BAD_REQUEST);
		}

		if ("payment_intent.succeeded".equals(event.getType())) {
			PaymentIntent paymentIntent = (PaymentIntent) event.getDataObjectDeserializer().getObject().orElse(null);

			if (paymentIntent != null && paymentIntent.getMetadata().containsKey("paymentId")) {
				Long paymentId = Long.parseLong(paymentIntent.getMetadata().get("paymentId"));
				String intentId = paymentIntent.getId();

				paymentService.handleSuccessfulWebhookPayment(paymentId, intentId);
			}
		}

		return ResponseEntity.ok("Success");
	}
}
