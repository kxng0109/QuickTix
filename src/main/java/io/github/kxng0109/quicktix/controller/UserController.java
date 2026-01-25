package io.github.kxng0109.quicktix.controller;

import io.github.kxng0109.quicktix.dto.request.CreateUserRequest;
import io.github.kxng0109.quicktix.dto.response.BookingResponse;
import io.github.kxng0109.quicktix.dto.response.UserResponse;
import io.github.kxng0109.quicktix.service.BookingService;
import io.github.kxng0109.quicktix.service.UserService;
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
public class UserController {

	private final UserService userService;
	private final BookingService bookingService;

	@PostMapping
	public ResponseEntity<UserResponse> createUser(
			@Valid @RequestBody CreateUserRequest request
	) {
		return new ResponseEntity<>(userService.createUser(request), HttpStatus.CREATED);
	}

	@GetMapping("/{id}")
	public ResponseEntity<UserResponse> getUserById(
			@PathVariable @Min(value = 1, message = "User ID must be greater than 0") long id
	) {
		return ResponseEntity.ok(userService.getUserById(id));
	}

	@GetMapping("/{userId}/bookings")
	public ResponseEntity<Page<BookingResponse>> getBookingsByUser(
			@Min(value = 1, message = "User ID must have a value of at least 1") @PathVariable long userId,
			Pageable pageable
	){
		return ResponseEntity.ok(bookingService.getBookingsByUser(
				userId,
				pageable
		));
	}

	@GetMapping("/email/{email}")
	public ResponseEntity<UserResponse> getUserByEmail(@PathVariable @Email(message = "Enter a valid email address") String email) {
		return ResponseEntity.ok(userService.getUserByEmail(email));
	}

	@PutMapping("/{id}")
	public ResponseEntity<UserResponse> updateUserById(
			@PathVariable @Min(value = 1, message = "User ID must at least 1") long id,
			@Valid @RequestBody CreateUserRequest request
	) {
		return new ResponseEntity<>(userService.updateUserById(id, request), HttpStatus.OK);
	}

	@DeleteMapping("/email/{email}")
	public ResponseEntity<Void> deleteUserByEmail(@PathVariable @Email(message = "Enter a valid email address") String email) {
		userService.deleteUserByEmail(email);

		return ResponseEntity.noContent().build();
	}
}
