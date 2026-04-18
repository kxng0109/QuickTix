package io.github.kxng0109.quicktix.integration;

import io.github.kxng0109.quicktix.dto.request.LoginRequest;
import io.github.kxng0109.quicktix.entity.User;
import io.github.kxng0109.quicktix.enums.Role;
import io.github.kxng0109.quicktix.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for all integration tests.
 * <p>
 * Annotations explained:
 *
 * @SpringBootTest — Loads the FULL Spring application context, including all beans,
 * configurations, and auto-configurations. This is different from
 * {@code @WebMvcTest} which only loads the web layer.
 *
 * @AutoConfigureMockMvc — Configures MockMvc to work with the full context.
 * We can still use MockMvc to make HTTP requests, but now
 * those requests go through the real service and repository layers.
 *
 * @ActiveProfiles("test") — Activates the "test" profile, which will load
 * application-test.properties. This lets us configure
 * a separate test database.
 *
 * @DirtiesContext — Resets the Spring context after each test class.
 * This ensures test isolation but makes tests slower.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)

public abstract class BaseIntegrationTest {

	@Autowired
	protected MockMvc mockMvc;

	@Autowired
	protected ObjectMapper objectMapper;

	@Autowired
	protected UserRepository userRepository;

	@Autowired
	protected PasswordEncoder passwordEncoder;

	protected String getUserToken() throws Exception{
		LoginRequest adminLoginRequest = LoginRequest.builder()
		                                             .email("jane.smith@example.com")
		                                             .password("password123")
		                                             .build();

		MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/login")
				                                           .contentType(MediaType.APPLICATION_JSON)
				                                           .content(objectMapper.writeValueAsString(adminLoginRequest)))
		                                  .andExpect(status().isOk())
		                                  .andReturn();

		return objectMapper.readTree(registerResult.getResponse().getContentAsString()).get("token").asText();
	}

	protected String getAdminToken() throws Exception {
		userRepository.save(User.builder()
		                                     .firstName("Admin")
		                                     .lastName("User")
		                                     .email("admin@test.com")
		                                     .passwordHash(passwordEncoder.encode("password123"))
		                                     .role(Role.ADMIN)
		                                     .build());

		LoginRequest adminLoginRequest = LoginRequest.builder()
		                                             .email("admin@test.com")
		                                             .password("password123")
		                                             .build();

		MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/login")
				                                           .contentType(MediaType.APPLICATION_JSON)
				                                           .content(objectMapper.writeValueAsString(adminLoginRequest)))
		                                  .andExpect(status().isOk())
		                                  .andReturn();

		return objectMapper.readTree(registerResult.getResponse().getContentAsString()).get("token").asText();
	}
}
