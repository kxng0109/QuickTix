package io.github.kxng0109.quicktix.controller;

import io.github.kxng0109.quicktix.dto.request.CreateUserRequest;
import io.github.kxng0109.quicktix.dto.response.BookingResponse;
import io.github.kxng0109.quicktix.dto.response.UserResponse;
import io.github.kxng0109.quicktix.service.BookingService;
import io.github.kxng0109.quicktix.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Validated
@Tag(name = "Users", description = "User registration and management")

public class UserController {

	private final UserService userService;
	private final BookingService bookingService;

	@Operation(
			summary = "Create a new user",
			description = "Registers a new user in the system. Email must be unique across all users."
	)
	@ApiResponses(value = {
			@ApiResponse(
					responseCode = "201",
					description = "User created successfully",
					content = @Content(
							mediaType = "application/json",
							schema = @Schema(implementation = UserResponse.class)
					)
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
											    "email": "Email can't be blank"
											}
											"""
							)
					)
			),
			@ApiResponse(
					responseCode = "409",
					description = "User with this email already exists",
					content = @Content(
							mediaType = "application/json",
							examples = @ExampleObject(
									value = """
											{
											    "statusCode": 409,
											    "message": "User with email already exists",
											    "path": "/api/v1/users",
											    "timestamp": "2026-01-28T12:00:00Z"
											}
											"""
							)
					)
			)
	})
	@PostMapping
	public ResponseEntity<UserResponse> createUser(
			@Valid @RequestBody CreateUserRequest request
	) {
		return new ResponseEntity<>(userService.createUser(request), HttpStatus.CREATED);
	}

	@Operation(
			summary = "Get user by ID",
			description = "Retrieves a user's details by their unique identifier"
	)
	@ApiResponses(value = {
			@ApiResponse(
					responseCode = "200",
					description = "User found",
					content = @Content(schema = @Schema(implementation = UserResponse.class))
			),
			@ApiResponse(
					responseCode = "400",
					description = "Invalid user ID - must be greater than 0",
					content = @Content
			),
			@ApiResponse(
					responseCode = "404",
					description = "User not found",
					content = @Content
			)
	})
	@GetMapping("/{id}")
	public ResponseEntity<UserResponse> getUserById(
			@PathVariable @Min(value = 1, message = "User ID must be greater than 0") long id
	) {
		return ResponseEntity.ok(userService.getUserById(id));
	}

	@Operation(
			summary = "Get user's bookings",
			description = "Retrieves all bookings made by a specific user with pagination support"
	)
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Bookings retrieved successfully"),
			@ApiResponse(responseCode = "400", description = "Invalid user ID", content = @Content),
			@ApiResponse(responseCode = "404", description = "User not found", content = @Content)
	})
	@GetMapping("/{userId}/bookings")
	public ResponseEntity<Page<BookingResponse>> getBookingsByUser(
			@Min(value = 1, message = "User ID must have a value of at least 1") @PathVariable long userId,
			Pageable pageable
	) {
		return ResponseEntity.ok(bookingService.getBookingsByUser(
				userId,
				pageable
		));
	}

	@Operation(
			summary = "Get user by email",
			description = "Retrieves a user's details by their email address"
	)
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "User found"),
			@ApiResponse(responseCode = "400", description = "Invalid email format", content = @Content),
			@ApiResponse(responseCode = "404", description = "User not found", content = @Content)
	})
	@GetMapping("/email/{email}")
	public ResponseEntity<UserResponse> getUserByEmail(@PathVariable @Email(message = "Enter a valid email address") String email) {
		return ResponseEntity.ok(userService.getUserByEmail(email));
	}

	@Operation(
			summary = "Update user",
			description = "Updates an existing user's details. If changing email, the new email must be unique."
	)
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "User updated successfully"),
			@ApiResponse(responseCode = "400", description = "Invalid request data", content = @Content),
			@ApiResponse(responseCode = "404", description = "User not found", content = @Content),
			@ApiResponse(responseCode = "409", description = "Email already in use by another user", content = @Content)
	})
	@PutMapping("/{id}")
	public ResponseEntity<UserResponse> updateUserById(
			@PathVariable @Min(value = 1, message = "User ID must at least 1") long id,
			@Valid @RequestBody CreateUserRequest request
	) {
		return new ResponseEntity<>(userService.updateUserById(id, request), HttpStatus.OK);
	}

	@Operation(
			summary = "Delete user by email",
			description = "Permanently deletes a user from the system. Users with existing bookings cannot be deleted - they should be deactivated instead."
	)
	@ApiResponses(value = {
			@ApiResponse(responseCode = "204", description = "User deleted successfully"),
			@ApiResponse(responseCode = "400", description = "Invalid email format", content = @Content),
			@ApiResponse(responseCode = "404", description = "User not found", content = @Content),
			@ApiResponse(responseCode = "409", description = "User has existing bookings and cannot be deleted", content = @Content)
	})
	@DeleteMapping("/email/{email}")
	public ResponseEntity<Void> deleteUserByEmail(@PathVariable @Email(message = "Enter a valid email address") String email) {
		userService.deleteUserByEmail(email);

		return ResponseEntity.noContent().build();
	}
}
