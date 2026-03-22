package io.github.kxng0109.quicktix.integration;

import io.github.kxng0109.quicktix.dto.request.CreateUserRequest;
import io.github.kxng0109.quicktix.dto.request.CreateVenueRequest;
import io.github.kxng0109.quicktix.dto.request.LoginRequest;
import io.github.kxng0109.quicktix.entity.User;
import io.github.kxng0109.quicktix.enums.Role;
import io.github.kxng0109.quicktix.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class SecurityIntegrationTest extends BaseIntegrationTest {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void fullAuthenticationFlow_shouldRegisterLoginAndAccessProtectedRoute() throws Exception {
		CreateUserRequest registerRequest = CreateUserRequest.builder()
		                                                     .firstName("Security")
		                                                     .lastName("Tester")
		                                                     .email("security.tester@example.com")
		                                                     .password("Password123!")
		                                                     .phoneNumber("+2348012345678")
		                                                     .build();

		mockMvc.perform(post("/api/v1/auth/register")
				                .contentType(MediaType.APPLICATION_JSON)
				                .content(objectMapper.writeValueAsString(registerRequest)))
		       .andExpect(status().isCreated())
		       .andExpect(jsonPath("$.token").exists())
		       .andExpect(jsonPath("$.role").value("USER"));

		LoginRequest loginRequest = new LoginRequest("security.tester@example.com", "Password123!");

		MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
				                                        .contentType(MediaType.APPLICATION_JSON)
				                                        .content(objectMapper.writeValueAsString(loginRequest)))
		                               .andExpect(status().isOk())
		                               .andExpect(jsonPath("$.token").exists())
		                               .andReturn();

		String jsonResponse = loginResult.getResponse().getContentAsString();
		String token = objectMapper.readTree(jsonResponse).get("token").asText();

		mockMvc.perform(get("/api/v1/users/me")
				                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("$.email").value("security.tester@example.com"))
		       .andExpect(jsonPath("$.firstName").value("Security"));
	}

	@Test
	void protectedRoute_shouldReturn401Unauthorized_whenNoTokenIsProvided() throws Exception {
		mockMvc.perform(get("/api/v1/users/me"))
		       .andExpect(status().isUnauthorized())
		       .andExpect(jsonPath("$.statusCode").value(401))
		       .andExpect(jsonPath("$.error").value("Unauthorized"))
		       .andExpect(jsonPath("$.message").value("Authentication required. Please provide a valid token."))
		       .andExpect(jsonPath("$.path").value("/api/v1/users/me"));
	}

	@Test
	void protectedRoute_shouldReturn401Unauthorized_whenTokenIsFakeOrInvalid() throws Exception {
		mockMvc.perform(get("/api/v1/users/me")
				                .header(HttpHeaders.AUTHORIZATION,
				                        "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.fake.payload"
				                ))
		       .andExpect(status().isUnauthorized())
		       .andExpect(jsonPath("$.statusCode").value(401));
	}

	@Test
	void protectedRoute_shouldReturn401Unauthorized_whenTokenIsMalformed() throws Exception {
		mockMvc.perform(get("/api/v1/users/me")
				                .header(HttpHeaders.AUTHORIZATION, "JustARandomStringNotAToken"))
		       .andExpect(status().isUnauthorized())
		       .andExpect(jsonPath("$.statusCode").value(401));
	}

	@Test
	void adminRoute_shouldReturn403Forbidden_whenAccessedByStandardUser() throws Exception {
		CreateUserRequest registerRequest = CreateUserRequest.builder()
		                                                     .firstName("Standard")
		                                                     .lastName("User")
		                                                     .email("standard.user@example.com")
		                                                     .password("Password123!")
		                                                     .build();

		MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
				                                           .contentType(MediaType.APPLICATION_JSON)
				                                           .content(objectMapper.writeValueAsString(registerRequest)))
		                                  .andExpect(status().isCreated())
		                                  .andReturn();

		String token = objectMapper.readTree(registerResult.getResponse().getContentAsString()).get("token").asText();

		CreateVenueRequest venueRequest = CreateVenueRequest.builder()
		                                                    .name("Forbidden Arena")
		                                                    .address("123 Street")
		                                                    .city("Lagos")
		                                                    .totalCapacity(500)
		                                                    .build();

		mockMvc.perform(post("/api/v1/venues")
				                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				                .contentType(MediaType.APPLICATION_JSON)
				                .content(objectMapper.writeValueAsString(venueRequest)))
		       .andExpect(status().isForbidden())
		       .andExpect(jsonPath("$.statusCode").value(403))
		       .andExpect(jsonPath("$.error").value("Forbidden"))
		       .andExpect(jsonPath("$.message").value(
				       "Access denied. You do not have permission to access this resource."))
		       .andExpect(jsonPath("$.path").value("/api/v1/venues"));
	}

	@Test
	void adminRoute_shouldReturn201Created_whenAccessedByAdminUser() throws Exception {
		User adminUser = User.builder()
		                     .firstName("Admin")
		                     .lastName("User")
		                     .email("admin@quicktix.com")
		                     .passwordHash(passwordEncoder.encode("AdminPass123!"))
		                     .role(Role.ADMIN)
		                     .build();
		userRepository.save(adminUser);

		LoginRequest loginRequest = new LoginRequest("admin@quicktix.com", "AdminPass123!");
		MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
				                                        .contentType(MediaType.APPLICATION_JSON)
				                                        .content(objectMapper.writeValueAsString(loginRequest)))
		                               .andExpect(status().isOk())
		                               .andReturn();

		String token = objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("token").asText();

		CreateVenueRequest venueRequest = CreateVenueRequest.builder()
		                                                    .name("Admin Created Arena")
		                                                    .address("456 Admin Ave")
		                                                    .city("Lagos")
		                                                    .totalCapacity(5000)
		                                                    .build();

		mockMvc.perform(post("/api/v1/venues")
				                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				                .contentType(MediaType.APPLICATION_JSON)
				                .content(objectMapper.writeValueAsString(venueRequest)))
		       .andExpect(status().isCreated())
		       .andExpect(jsonPath("$.name").value("Admin Created Arena"));
	}
}