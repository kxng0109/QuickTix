package io.github.kxng0109.quicktix.controller;

import io.github.kxng0109.quicktix.dto.request.CreateEventRequest;
import io.github.kxng0109.quicktix.dto.request.EventDateSearchRequest;
import io.github.kxng0109.quicktix.dto.response.EventResponse;
import io.github.kxng0109.quicktix.dto.response.SeatResponse;
import io.github.kxng0109.quicktix.service.EventService;
import io.github.kxng0109.quicktix.service.SeatService;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@Validated
@Tag(name = "Events", description = "Event creation, scheduling, and management")
public class EventController {

	private final EventService eventService;
	private final SeatService seatService;

	@Operation(
			summary = "Create a new event",
			description = """
					Creates a new event at the specified venue. Seats are automatically generated based on the `numberOfSeats` parameter.
					
					The event will be created with status `UPCOMING`. Status transitions:
					- `UPCOMING` → `ONGOING` (when event start time is reached)
					- `ONGOING` → `COMPLETED` (when event end time is reached)
					- Any status → `CANCELLED` (manual cancellation)
					"""
	)
	@ApiResponses(value = {
			@ApiResponse(
					responseCode = "201",
					description = "Event created successfully with seats generated",
					content = @Content(schema = @Schema(implementation = EventResponse.class))
			),
			@ApiResponse(responseCode = "400", description = "Invalid request data", content = @Content),
			@ApiResponse(responseCode = "404", description = "Venue not found", content = @Content)
	})
	@PostMapping
	public ResponseEntity<EventResponse> createEvent(
			@Valid @RequestBody CreateEventRequest request
	) {
		return new ResponseEntity<>(eventService.createEvent(request), HttpStatus.CREATED);
	}

	@Operation(
			summary = "Get event by ID",
			description = "Retrieves an event's details including the current number of available seats"
	)
	@ApiResponses(value = {
			@ApiResponse(
					responseCode = "200",
					description = "Event found",
					content = @Content(schema = @Schema(implementation = EventResponse.class))
			),
			@ApiResponse(responseCode = "400", description = "Invalid event ID", content = @Content),
			@ApiResponse(responseCode = "404", description = "Event not found", content = @Content)
	})
	@GetMapping("/{id}")
	public ResponseEntity<EventResponse> getEventById(
			@Min(value = 1, message = "Event ID must have a value of at least 1") @PathVariable long id
	) {
		return ResponseEntity.ok(eventService.getEventById(id));
	}

	@Operation(
			summary = "Get all upcoming events",
			description = "Retrieves all events with status `UPCOMING` with pagination support"
	)
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Upcoming events retrieved successfully")
	})
	@GetMapping("/upcoming")
	public ResponseEntity<Page<EventResponse>> getAllUpcomingEvents(
			Pageable pageable
	) {
		return ResponseEntity.ok(eventService.getAllUpcomingEvents(pageable));
	}

	@Operation(
			summary = "Get all seats for an event",
			description = "Retrieves all seats (available, held, and booked) for a specific event with pagination support"
	)
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Seats retrieved successfully"),
			@ApiResponse(responseCode = "400", description = "Invalid event ID", content = @Content),
			@ApiResponse(responseCode = "404", description = "Event not found", content = @Content)
	})
	@GetMapping("/{eventId}/seats")
	public ResponseEntity<Page<SeatResponse>> getAllSeatsByEvent(
			@Min(value = 1, message = "Event ID must have a value of at least 1") @PathVariable long eventId,
			Pageable pageable
	) {
		return ResponseEntity.ok(seatService.getAllSeatsByEvent(eventId, pageable));
	}

	@Operation(
			summary = "Get available seats for an event",
			description = "Retrieves only seats with status `AVAILABLE` for a specific event. Use this endpoint when displaying seat selection to users."
	)
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Available seats retrieved successfully"),
			@ApiResponse(responseCode = "400", description = "Invalid event ID", content = @Content),
			@ApiResponse(responseCode = "404", description = "Event not found", content = @Content)
	})
	@GetMapping("/{eventId}/seats/available")
	public ResponseEntity<Page<SeatResponse>> getAllAvailableSeats(
			@Min(value = 1, message = "Event ID must have a value of at least 1") @PathVariable long eventId,
			Pageable pageable
	) {
		return ResponseEntity.ok(seatService.getAvailableSeats(eventId, pageable));
	}

	@Operation(
			summary = "Get events by venue",
			description = "Retrieves all events scheduled at a specific venue with pagination support"
	)
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Events retrieved successfully"),
			@ApiResponse(responseCode = "400", description = "Invalid venue ID", content = @Content),
			@ApiResponse(responseCode = "404", description = "Venue not found", content = @Content)
	})
	@GetMapping("/venue/{venueId}")
	public ResponseEntity<Page<EventResponse>> getEventsByVenueId(
			@Min(value = 1, message = "Venue ID must have a value of at least 1") @PathVariable long venueId,
			Pageable pageable
	) {
		return ResponseEntity.ok(eventService.getEventsByVenueId(venueId, pageable));
	}

	@Operation(
			summary = "Get events by date range",
			description = "Retrieves all events starting within the specified date range. Both `startDate` and `endDate` are required, and `startDate` must be before `endDate`."
	)
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Events retrieved successfully"),
			@ApiResponse(responseCode = "400", description = "Invalid date range - start date must be before end date", content = @Content)
	})
	@GetMapping("/date-range")
	public ResponseEntity<Page<EventResponse>> getEventsByDateRange(
			@Valid EventDateSearchRequest request,
			Pageable pageable
	) {
		return ResponseEntity.ok(eventService.getEventsByDateRange(request, pageable));
	}

	@Operation(
			summary = "Update event",
			description = """
					Updates an existing event's details. Note:
					- The number of seats cannot be changed after creation
					- Changing the venue is allowed
					- Event date/time can be updated
					"""
	)
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Event updated successfully"),
			@ApiResponse(responseCode = "400", description = "Invalid request data or attempting to change seat count", content = @Content),
			@ApiResponse(responseCode = "404", description = "Event or venue not found", content = @Content)
	})
	@PutMapping("/{id}")
	public ResponseEntity<EventResponse> updateEventById(
			@Min(value = 1, message = "Event ID must have a value of at least 1") @PathVariable long id,
			@Valid @RequestBody CreateEventRequest request
	) {
		return ResponseEntity.ok(eventService.updateEventById(id, request));
	}
}
