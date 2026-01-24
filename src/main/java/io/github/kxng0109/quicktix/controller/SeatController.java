package io.github.kxng0109.quicktix.controller;

import io.github.kxng0109.quicktix.dto.request.HoldSeatsRequest;
import io.github.kxng0109.quicktix.dto.response.SeatResponse;
import io.github.kxng0109.quicktix.service.SeatService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
public class SeatController {

	private final SeatService seatService;

	@GetMapping("/events/{eventId}/seats")
	public ResponseEntity<Page<SeatResponse>> getAllSeatsByEvent(
			@Min(value = 1, message = "Event ID must have a value of at least 1") @PathVariable long eventId,
			Pageable pageable
	) {
		return ResponseEntity.ok(seatService.getAllSeatsByEvent(eventId, pageable));
	}

	@GetMapping("/events/{eventId}/seats/available")
	public ResponseEntity<Page<SeatResponse>> getAllAvailableSeats(
			@Min(value = 1, message = "Event ID must have a value of at least 1") @PathVariable long eventId,
			Pageable pageable
	) {
		return ResponseEntity.ok(seatService.getAvailableSeats(eventId, pageable));
	}

	@PostMapping("/seats/hold")
	public ResponseEntity<List<SeatResponse>> holdSeats(
			@Valid @RequestBody HoldSeatsRequest request
	) {
		return new ResponseEntity<>(seatService.holdSeats(request), HttpStatus.CREATED);
	}

	@PostMapping("/seats/release")
	public ResponseEntity<Void> releaseSeats(
			@Valid @RequestBody HoldSeatsRequest request
	) {
		seatService.releaseSeats(request);
		return ResponseEntity.noContent().build();
	}
}
