package io.github.kxng0109.quicktix.controller;

import tools.jackson.databind.ObjectMapper;
import io.github.kxng0109.quicktix.dto.request.CreateUserRequest;
import io.github.kxng0109.quicktix.dto.request.LoginRequest;
import io.github.kxng0109.quicktix.dto.response.AuthResponse;
import io.github.kxng0109.quicktix.exception.UserExistsException;
import io.github.kxng0109.quicktix.service.AuthService;
import io.github.kxng0109.quicktix.service.CustomUserDetailsService;
import io.github.kxng0109.quicktix.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@RateLimitedWebTest
public class AuthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@MockitoBean
	private AuthService authService;

	@MockitoBean
	private JwtService jwtService;

	@MockitoBean
	private CustomUserDetailsService userDetailsService;

	private CreateUserRequest registerRequest;
	private LoginRequest loginRequest;
	private AuthResponse authResponse;

	@BeforeEach
	public void setup() {
		registerRequest = CreateUserRequest.builder()
		                                   .firstName("John")
		                                   .lastName("Doe")
		                                   .email("john.doe@example.com")
		                                   .password("Password123!")
		                                   .build();

		loginRequest = new LoginRequest("john.doe@example.com", "Password123!");

		authResponse = AuthResponse.builder()
		                           .token("eyJhbGciOiJIUzI1NiJ9...")
		                           .tokenType("Bearer")
		                           .expiresIn(86400L)
		                           .email("john.doe@example.com")
		                           .role("USER")
		                           .build();
	}

	@Test
	public void handleRegistration_should_return201Created_whenRequestIsValid() throws Exception {
		when(authService.handleRegistration(any(CreateUserRequest.class))).thenReturn(authResponse);

		mockMvc.perform(post("/api/v1/auth/register")
				                .contentType(MediaType.APPLICATION_JSON)
				                .content(objectMapper.writeValueAsString(registerRequest)))
		       .andExpect(status().isCreated())
		       .andExpect(jsonPath("$.token").value(authResponse.token()))
		       .andExpect(jsonPath("$.role").value("USER"));
	}

	@Test
	public void handleRegistration_should_return409Conflict_whenEmailExists() throws Exception {
		when(authService.handleRegistration(any(CreateUserRequest.class)))
				.thenThrow(new UserExistsException());

		mockMvc.perform(post("/api/v1/auth/register")
				                .contentType(MediaType.APPLICATION_JSON)
				                .content(objectMapper.writeValueAsString(registerRequest)))
		       .andExpect(status().isConflict())
		       .andExpect(jsonPath("$.statusCode").value(409));
	}

	@Test
	public void handleLogin_should_return200Ok_whenCredentialsAreValid() throws Exception {
		when(authService.handleLogin(any(LoginRequest.class))).thenReturn(authResponse);

		mockMvc.perform(post("/api/v1/auth/login")
				                .contentType(MediaType.APPLICATION_JSON)
				                .content(objectMapper.writeValueAsString(loginRequest)))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("$.token").exists());
	}

	@Test
	public void handleLogin_should_return401Unauthorized_whenCredentialsAreInvalid() throws Exception {
		when(authService.handleLogin(any(LoginRequest.class)))
				.thenThrow(new BadCredentialsException("Bad credentials"));

		mockMvc.perform(post("/api/v1/auth/login")
				                .contentType(MediaType.APPLICATION_JSON)
				                .content(objectMapper.writeValueAsString(loginRequest)))
		       .andExpect(status().isUnauthorized())
		       .andExpect(jsonPath("$.statusCode").value(401));
	}
}