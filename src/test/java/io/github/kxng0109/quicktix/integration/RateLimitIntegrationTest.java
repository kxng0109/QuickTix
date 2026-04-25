package io.github.kxng0109.quicktix.integration;

import io.github.kxng0109.quicktix.dto.request.LoginRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RateLimitIntegrationTest extends BaseIntegrationTest {

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	@DisplayName("Tier 1: IP DDoS Shield should block traffic after 15 requests")
	void testIpBasedRateLimit() throws Exception {
		String testIp = "192.168.1.55";

		for (int i = 1; i <= 15; i++) {
			mockMvc.perform(post("/api/v1/auth/login")
					                .contentType(MediaType.APPLICATION_JSON)
					                .content(
											objectMapper.writeValueAsString(
												LoginRequest.builder().email("test@test.com").password("password").build()
											)
					                )
					                .with(request -> {
						                request.setRemoteAddr(testIp);
						                return request;
					                }))
			       .andExpect(status().isUnauthorized());
		}

		mockMvc.perform(post("/api/v1/auth/login")
				                .contentType(MediaType.APPLICATION_JSON)
				                .content(
						                objectMapper.writeValueAsString(
								                LoginRequest.builder().email("test@test.com").password("password").build()
						                )
				                )
				                .with(request -> {
					                request.setRemoteAddr(testIp);
					                return request;
				                }))
		       .andExpect(status().isTooManyRequests())
		       .andExpect(header().exists("X-Rate-Limit-Retry-After-Seconds"));
	}

	@Test
	@DisplayName("Tier 2: Global User Shield should block authenticated scraping after 14 requests")
	void testUserBasedRateLimit() throws Exception {
		String userToken = createUserAndGetToken("scraper@unknown.domain");

		// Fire 14 authenticated requests
		for (int i = 1; i <= 14; i++) {
			mockMvc.perform(get("/api/v1/users/me")
					                // Ensure the IP filter doesn't trip first by using a fresh IP
					                .header("Authorization", "Bearer " + userToken)
					                .with(request -> {
						                request.setRemoteAddr("10.0.0.99");
						                return request;
					                }))
			       .andExpect(status().isOk());
		}

		// Request 15 MUST fail
		mockMvc.perform(get("/api/v1/users/me")
				                .header("Authorization", "Bearer " + userToken)
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