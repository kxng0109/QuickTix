package io.github.kxng0109.quicktix.controller;

import io.github.kxng0109.quicktix.controller.webhook.StripeWebhookController;
import io.github.kxng0109.quicktix.service.CustomUserDetailsService;
import io.github.kxng0109.quicktix.service.JwtService;
import io.github.kxng0109.quicktix.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StripeWebhookController.class)
@RateLimitedWebTest
public class StripeWebhookControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private CustomUserDetailsService userDetailsService;

	@MockitoBean
	private JwtService jwtService;

	@MockitoBean
	private PaymentService paymentService;

	@Test
	public void handleStripeEvent_should_return400BadRequest_when_signatureIsInvalid() throws Exception {
		String fakePayload = "{\"id\":\"evt_test_123\",\"type\":\"payment_intent.succeeded\"}";
		String fakeSignature = "t=123,v1=fake_signature_hash";

		// This tests that the endpoint is reachable (not returning 401/403)
		// but correctly rejects bad cryptographic signatures with a 400 Bad Request.
		mockMvc.perform(post("/api/v1/webhooks/stripe")
				                .header("Stripe-Signature", fakeSignature)
				                .contentType(MediaType.APPLICATION_JSON)
				                .content(fakePayload))
		       .andExpect(status().isBadRequest());
	}

	@Test
	public void handleStripeEvent_should_return400BadRequest_when_signatureIsMissing() throws Exception {
		String fakePayload = "{\"id\":\"evt_test_123\"}";

		mockMvc.perform(post("/api/v1/webhooks/stripe")
				                .contentType(MediaType.APPLICATION_JSON)
				                .content(fakePayload))
		       .andExpect(status().isBadRequest());
	}
}
