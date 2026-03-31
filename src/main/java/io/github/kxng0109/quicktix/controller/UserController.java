package io.github.kxng0109.quicktix.controller;

import io.github.kxng0109.quicktix.dto.request.CreateUserRequest;
import io.github.kxng0109.quicktix.dto.response.BookingResponse;
import io.github.kxng0109.quicktix.dto.response.UserResponse;
import io.github.kxng0109.quicktix.entity.User;
import io.github.kxng0109.quicktix.service.BookingService;
import io.github.kxng0109.quicktix.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
			summary = "Get user by ID (ADMIN ONLY)",
			description = "Retrieves a user's details by their unique identifier. Accessible only to administrators."
	)
	@ApiResponses(value = {
			@ApiResponse(
					responseCode = "200",
					description = "User retrieved successfully",
					content = @Content(schema = @Schema(implementation = UserResponse.class))
			),
			@ApiResponse(
					responseCode = "400",
					description = "Invalid user ID - must be greater than 0",
					content = @Content
			),
			@ApiResponse(
					responseCode = "401",
					description = "Authentication required",
					content = @Content
			),
			@ApiResponse(
					responseCode = "403",
					description = "Access denied - ADMIN role required",
					content = @Content
			),
			@ApiResponse(
					responseCode = "404",
					description = "User not found",
					content = @Content
			)
	})
	@PreAuthorize("hasRole('ADMIN')")
	@GetMapping("/{id}")
	public ResponseEntity<UserResponse> getUserById(
			@PathVariable @Min(value = 1, message = "User ID must be greater than 0") long id
	) {
		return ResponseEntity.ok(userService.getUserById(id));
	}

	@Operation(
			summary = "Get bookings for a user",
			description = "Retrieves bookings for the specified user ID with pagination. " +
					"ADMIN can access any user's bookings. USER can access only their own bookings."
	)
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Bookings retrieved successfully"),
			@ApiResponse(responseCode = "400", description = "Invalid user ID", content = @Content),
			@ApiResponse(responseCode = "401", description = "Authentication required", content = @Content),
			@ApiResponse(responseCode = "403", description = "Access denied", content = @Content),
			@ApiResponse(responseCode = "404", description = "User not found", content = @Content)
	})
	@PreAuthorize("hasAnyRole('ADMIN', 'USER')")
	@GetMapping("/{userId}/bookings")
	public ResponseEntity<Page<BookingResponse>> getBookingsByUser(
			@Min(value = 1, message = "User ID must have a value of at least 1") @PathVariable long userId,
			Pageable pageable,
			@AuthenticationPrincipal User currentUser
	) {
		return ResponseEntity.ok(bookingService.getBookingsByUser(
				userId,
				pageable,
				currentUser
		));
	}

	@Operation(
			summary = "Get current authenticated user profile",
			description = "Retrieves the profile details of the currently authenticated user."
	)
	@ApiResponses(value = {
			@ApiResponse(
					responseCode = "200",
					description = "Current user retrieved successfully",
					content = @Content(schema = @Schema(implementation = UserResponse.class))
			),
			@ApiResponse(responseCode = "401", description = "Authentication required", content = @Content),
			@ApiResponse(responseCode = "404", description = "User not found", content = @Content)
	})
	@PreAuthorize("hasAnyRole('ADMIN', 'USER')")
	@GetMapping("/me")
	public ResponseEntity<UserResponse> getUser(
			@AuthenticationPrincipal User currentUser
	) {
		return ResponseEntity.ok(userService.getUser(currentUser));
	}

	@Operation(
			summary = "Update current authenticated user profile",
			description = "Updates the profile details of the currently authenticated user. " +
					"If email is changed, the new email must be unique."
	)
	@ApiResponses(value = {
			@ApiResponse(
					responseCode = "200",
					description = "User profile updated successfully",
					content = @Content(schema = @Schema(implementation = UserResponse.class))
			),
			@ApiResponse(responseCode = "400", description = "Invalid request data", content = @Content),
			@ApiResponse(responseCode = "401", description = "Authentication required", content = @Content),
			@ApiResponse(responseCode = "404", description = "User not found", content = @Content),
			@ApiResponse(responseCode = "409", description = "Email already in use by another user", content = @Content)
	})
	@PreAuthorize("hasAnyRole('ADMIN', 'USER')")
	@PutMapping("/me")
	public ResponseEntity<UserResponse> updateUser(
			@Valid @RequestBody CreateUserRequest request,
			@AuthenticationPrincipal User currentUser
	) {
		return new ResponseEntity<>(userService.updateUser(request, currentUser), HttpStatus.OK);
	}

	/*@Operation(
			summary = "Update user by ID (ADMIN ONLY)",
			description = "Updates an existing user's details by ID. Accessible only to administrators. " +
					"If email is changed, the new email must be unique."
	)
	@ApiResponses(value = {
			@ApiResponse(
					responseCode = "200",
					description = "User updated successfully",
					content = @Content(schema = @Schema(implementation = UserResponse.class))
			),
			@ApiResponse(responseCode = "400", description = "Invalid request data or user ID", content = @Content),
			@ApiResponse(responseCode = "401", description = "Authentication required", content = @Content),
			@ApiResponse(responseCode = "403", description = "Access denied - ADMIN role required", content = @Content),
			@ApiResponse(responseCode = "404", description = "User not found", content = @Content),
			@ApiResponse(responseCode = "409", description = "Email already in use by another user", content = @Content)
	})
	@PreAuthorize("hasRole('ADMIN')")
	@PutMapping("/{id}")
	public ResponseEntity<UserResponse> updateUserById(
			@PathVariable @Min(value = 1, message = "User ID must at least 1") long id,
			@Valid @RequestBody CreateUserRequest request,
			@AuthenticationPrincipal User currentUser
	) {
		return new ResponseEntity<>(userService.updateUserById(id, request, currentUser), HttpStatus.OK);
	}*/

	@Operation(
			summary = "Delete current authenticated user",
			description = "Permanently deletes the currently authenticated user. " +
					"Users with existing bookings cannot be deleted and should be deactivated instead."
	)
	@ApiResponses(value = {
			@ApiResponse(responseCode = "204", description = "User deleted successfully"),
			@ApiResponse(responseCode = "401", description = "Authentication required", content = @Content),
			@ApiResponse(responseCode = "404", description = "User not found", content = @Content),
			@ApiResponse(responseCode = "409", description = "User has existing bookings and cannot be deleted", content = @Content)
	})
	@PreAuthorize("hasAnyRole('ADMIN', 'USER')")
	@DeleteMapping("/me")
	public ResponseEntity<Void> deleteUser(
			@AuthenticationPrincipal User currentUser
	) {
		userService.deleteUser(currentUser);

		return ResponseEntity.noContent().build();
	}
}
