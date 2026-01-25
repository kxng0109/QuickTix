package io.github.kxng0109.quicktix.controller;

import io.github.kxng0109.quicktix.dto.request.CreateEventRequest;
import io.github.kxng0109.quicktix.dto.request.EventDateSearchRequest;
import io.github.kxng0109.quicktix.dto.response.EventResponse;
import io.github.kxng0109.quicktix.dto.response.SeatResponse;
import io.github.kxng0109.quicktix.service.EventService;
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

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@Validated
public class EventController {

	private final EventService eventService;
	private final SeatService seatService;

	@PostMapping
	public ResponseEntity<EventResponse> createEvent(
			@Valid @RequestBody CreateEventRequest request
	) {
		return new ResponseEntity<>(eventService.createEvent(request), HttpStatus.CREATED);
	}

	@GetMapping("/{id}")
	public ResponseEntity<EventResponse> getEventById(
			@Min(value = 1, message = "Event ID must have a value of at least 1") @PathVariable long id
	) {
		return ResponseEntity.ok(eventService.getEventById(id));
	}

	@GetMapping("/upcoming")
	public ResponseEntity<Page<EventResponse>> getAllUpcomingEvents(
			Pageable pageable
	) {
		return ResponseEntity.ok(eventService.getAllUpcomingEvents(pageable));
	}

	@GetMapping("/{eventId}/seats")
	public ResponseEntity<Page<SeatResponse>> getAllSeatsByEvent(
			@Min(value = 1, message = "Event ID must have a value of at least 1") @PathVariable long eventId,
			Pageable pageable
	) {
		return ResponseEntity.ok(seatService.getAllSeatsByEvent(eventId, pageable));
	}

	@GetMapping("/{eventId}/seats/available")
	public ResponseEntity<Page<SeatResponse>> getAllAvailableSeats(
			@Min(value = 1, message = "Event ID must have a value of at least 1") @PathVariable long eventId,
			Pageable pageable
	) {
		return ResponseEntity.ok(seatService.getAvailableSeats(eventId, pageable));
	}

	@GetMapping("/venue/{venueId}")
	public ResponseEntity<Page<EventResponse>> getEventsByVenueId(
			@Min(value = 1, message = "Venue ID must have a value of at least 1") @PathVariable long venueId,
			Pageable pageable
	) {
		return ResponseEntity.ok(eventService.getEventsByVenueId(venueId, pageable));
	}

	@GetMapping("/date-range")
	public ResponseEntity<Page<EventResponse>> getEventsByDateRange(
			@Valid EventDateSearchRequest request,
			Pageable pageable
	) {
		return ResponseEntity.ok(eventService.getEventsByDateRange(request, pageable));
	}

	@PutMapping("/{id}")
	public ResponseEntity<EventResponse> updateEventById(
			@Min(value = 1, message = "Event ID must have a value of at least 1") @PathVariable long id,
			@Valid @RequestBody CreateEventRequest request
	) {
		return ResponseEntity.ok(eventService.updateEventById(id, request));
	}
}
