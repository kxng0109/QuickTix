package io.github.kxng0109.quicktix.controller;

import io.github.kxng0109.quicktix.dto.request.CreateUserRequest;
import io.github.kxng0109.quicktix.dto.request.LoginRequest;
import io.github.kxng0109.quicktix.dto.response.AuthResponse;
import io.github.kxng0109.quicktix.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User registration and login")
public class AuthController {

	private final AuthService authService;

	@Operation(
			summary = "Register a new user",
			description = """
					Creates a new user account and returns a JWT token.
					
					After successful registration, the user is immediately authenticated
					and can use the returned token for subsequent requests.
					
					**Password requirements:**
					- Minimum 8 characters
					
					**Email:**
					- Must be unique across all users
					- Used as the login identifier
					"""
	)
	@ApiResponses(value = {
			@ApiResponse(
					responseCode = "201",
					description = "User registered successfully",
					content = @Content(schema = @Schema(implementation = AuthResponse.class))
			),
			@ApiResponse(
					responseCode = "400",
					description = "Invalid request data - validation failed",
					content = @Content(
							mediaType = "application/json",
							examples = @ExampleObject(
									value = """
											{
											    "firstName": "First name can't be blank",
											    "email": "Email must be valid",
											    "password": "Password must be at least 8 characters"
											}
											"""
							)
					)
			),
			@ApiResponse(
					responseCode = "409",
					description = "Email already registered",
					content = @Content(
							mediaType = "application/json",
							examples = @ExampleObject(
									value = """
											{
											    "statusCode": 409,
											    "error": "Conflict",
											    "message": "User with this email exists.",
											    "path": "/api/v1/auth/register",
											    "timestamp": "2026-01-28T12:00:00+01:00"
											}
											"""
							)
					)
			)
	})
	@PostMapping("/register")
	public ResponseEntity<AuthResponse> handleRegistration(
			@Validated @RequestBody CreateUserRequest request
	) {
		return new ResponseEntity<>(authService.handleRegistration(request), HttpStatus.CREATED);
	}

	@Operation(
			summary = "Login",
			description = """
					Authenticates a user with email and password, returning a JWT token.
					
					**Usage:**
					1. Call this endpoint with valid credentials
					2. Store the returned token
					3. Include the token in subsequent requests:
					   `Authorization: Bearer <token>`
					
					**Token validity:**
					- Tokens expire after 24 hours (configurable)
					- After expiration, the user must login again
					"""
	)
	@ApiResponses(value = {
			@ApiResponse(
					responseCode = "200",
					description = "Login successful",
					content = @Content(schema = @Schema(implementation = AuthResponse.class))
			),
			@ApiResponse(
					responseCode = "400",
					description = "Invalid request data - validation failed",
					content = @Content(
							mediaType = "application/json",
							examples = @ExampleObject(
									value = """
											{
											    "email": "Email can't be blank",
											    "password": "Password can't be blank"
											}
											"""
							)
					)
			),
			@ApiResponse(
					responseCode = "401",
					description = "Invalid credentials",
					content = @Content(
							mediaType = "application/json",
							examples = @ExampleObject(
									value = """
											{
											    "statusCode": 401,
											    "error": "Unauthorized",
											    "message": "Bad credentials",
											    "path": "/api/v1/auth/login",
											    "timestamp": "2026-01-28T12:00:00+01:00"
											}
											"""
							)
					)
			)
	})

	@PostMapping("/login")
	public ResponseEntity<AuthResponse> handleLogin(
			@Validated @RequestBody LoginRequest request
	) {
		return ResponseEntity.ok(authService.handleLogin(request));
	}
}
