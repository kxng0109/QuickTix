package io.github.kxng0109.quicktix.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RateLimitIntegrationTest extends BaseIntegrationTest {

	@Test
	@DisplayName("Tier 1: IP DDoS Shield should block traffic after 7 requests")
	void testIpBasedRateLimit() throws Exception {
		String testIp = "192.168.1.55";

		for (int i = 1; i <= 7; i++) {
			mockMvc.perform(post("/api/v1/auth/login")
					                .contentType(MediaType.APPLICATION_JSON)
					                .content("{\"email\":\"test@test.com\",\"password\":\"password\"}")
					                .with(request -> {
						                request.setRemoteAddr(testIp);
						                return request;
					                }))
			       .andExpect(status().isUnauthorized());
		}

		mockMvc.perform(post("/api/v1/auth/login")
				                .contentType(MediaType.APPLICATION_JSON)
				                .content("{\"email\":\"test@test.com\",\"password\":\"password\"}")
				                .with(request -> {
					                request.setRemoteAddr(testIp);
					                return request;
				                }))
		       .andExpect(status().isTooManyRequests())
		       .andExpect(header().exists("X-Rate-Limit-Retry-After-Seconds"));
	}

	@Test
	@DisplayName("Tier 2: Global User Shield should block authenticated scraping after 6 requests")
	void testUserBasedRateLimit() throws Exception {
		String testEmail = "scraper@unilag.edu";

		// Fire 6 authenticated requests
		for (int i = 1; i <= 6; i++) {
			mockMvc.perform(get("/api/v1/events/upcoming")
					                .with(user(testEmail).roles("USER"))
					                // Ensure the IP filter doesn't trip first by using a fresh IP
					                .with(request -> {
						                request.setRemoteAddr("10.0.0.99");
						                return request;
					                }))
			       .andExpect(status().isOk());
		}

		// Request 7 MUST fail
		mockMvc.perform(get("/api/v1/events/upcoming")
				                .with(user(testEmail).roles("USER"))
				                .with(request -> {
					                request.setRemoteAddr("10.0.0.99");
					                return request;
				                }))
		       .andExpect(status().isTooManyRequests());
	}

	@Test
	@DisplayName("Tier 3: Critical Action Shield should block aggressive seat holding after 5 requests")
	void testCriticalActionRateLimit() throws Exception {
		String scalperEmail = "scalper-bot@vpn.com";

		// Fire 5 aggressive checkout attempts
		for (int i = 1; i <= 5; i++) {
			mockMvc.perform(post("/api/v1/seats/hold")
					                .contentType(MediaType.APPLICATION_JSON)
					                .content("{}") // Dummy payload
					                .with(user(scalperEmail).roles("USER"))
					                .with(request -> {
						                request.setRemoteAddr("10.0.0.77");
						                return request;
					                }))
			       // Expect business logic failures (400, 404, etc), but NOT 429
			       .andExpect(status().is4xxClientError());
		}

		// Attempt 6 MUST be instantly blocked by Tier 3
		mockMvc.perform(post("/api/v1/seats/hold")
				                .contentType(MediaType.APPLICATION_JSON)
				                .content("{}")
				                .with(user(scalperEmail).roles("USER"))
				                .with(request -> {
					                request.setRemoteAddr("10.0.0.77");
					                return request;
				                }))
		       .andExpect(status().isTooManyRequests());
	}
}