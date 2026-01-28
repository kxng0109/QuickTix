package io.github.kxng0109.quicktix.controller;

import io.github.kxng0109.quicktix.dto.request.HoldSeatsRequest;
import io.github.kxng0109.quicktix.dto.response.SeatResponse;
import io.github.kxng0109.quicktix.service.SeatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/seats")
@RequiredArgsConstructor
@Validated
@Tag(name = "Seats", description = "Seat availability, holding, and releasing")
public class SeatController {

	private final SeatService seatService;

	@Operation(
			summary = "Hold seats",
			description = """
					Temporarily holds selected seats for a user. Held seats are reserved for 15 minutes.
					
					**Important:**
					- Uses pessimistic locking to prevent race conditions
					- If seats are already held by another user, returns 400 Bad Request
					- If the same user already holds these seats, the hold is refreshed
					- Seats must belong to the specified event
					
					**Next step:** After holding seats, create a booking using the `/api/v1/bookings` endpoint.
					"""
	)
	@ApiResponses(value = {
			@ApiResponse(
					responseCode = "201",
					description = "Seats held successfully",
					content = @Content(schema = @Schema(implementation = SeatResponse.class))
			),
			@ApiResponse(
					responseCode = "400",
					description = "Seats not available or already held by another user",
					content = @Content(
							mediaType = "application/json",
							examples = @ExampleObject(
									value = """
											{
											    "statusCode": 400,
											    "message": "Seat is not available",
											    "path": "/api/v1/seats/hold",
											    "timestamp": "2026-01-28T12:00:00Z"
											}
											"""
							)
					)
			),
			@ApiResponse(responseCode = "404", description = "Event or user not found", content = @Content)
	})
	@PostMapping("/hold")
	public ResponseEntity<List<SeatResponse>> holdSeats(
			@Valid @RequestBody HoldSeatsRequest request
	) {
		return new ResponseEntity<>(seatService.holdSeats(request), HttpStatus.CREATED);
	}

	@Operation(
			summary = "Release seats",
			description = """
					Releases previously held seats back to available status.
					
					**Rules:**
					- Only the user who holds the seats can release them
					- Seats must be in HELD status (not BOOKED)
					- If seats are already released, the operation is idempotent
					"""
	)
	@ApiResponses(value = {
			@ApiResponse(responseCode = "204", description = "Seats released successfully"),
			@ApiResponse(
					responseCode = "400",
					description = "Cannot release seats - user does not hold these seats",
					content = @Content
			),
			@ApiResponse(responseCode = "404", description = "Event or user not found", content = @Content)
	})
	@PostMapping("/release")
	public ResponseEntity<Void> releaseSeats(
			@Valid @RequestBody HoldSeatsRequest request
	) {
		seatService.releaseSeats(request);
		return ResponseEntity.noContent().build();
	}
}
