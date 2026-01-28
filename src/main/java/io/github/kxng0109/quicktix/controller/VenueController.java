package io.github.kxng0109.quicktix.controller;

import io.github.kxng0109.quicktix.dto.request.CreateVenueRequest;
import io.github.kxng0109.quicktix.dto.response.VenueResponse;
import io.github.kxng0109.quicktix.service.VenueService;
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
@RequestMapping("/api/v1/venues")
@RequiredArgsConstructor
@Validated
@Tag(name = "Venues", description = "Venue creation and management")
public class VenueController {

	private final VenueService venueService;

	@Operation(
			summary = "Create a new venue",
			description = "Creates a new venue where events can be hosted. Venues have a name, address, city, and total seating capacity."
	)
	@ApiResponses(value = {
			@ApiResponse(
					responseCode = "201",
					description = "Venue created successfully",
					content = @Content(schema = @Schema(implementation = VenueResponse.class))
			),
			@ApiResponse(
					responseCode = "400",
					description = "Invalid request data - validation failed",
					content = @Content
			)
	})
	@PostMapping
	public ResponseEntity<VenueResponse> createVenue(@Valid @RequestBody CreateVenueRequest request) {
		return new ResponseEntity<>(venueService.createVenue(request), HttpStatus.CREATED);
	}

	@Operation(
			summary = "Get venue by ID",
			description = "Retrieves a venue's details by its unique identifier"
	)
	@ApiResponses(value = {
			@ApiResponse(
					responseCode = "200",
					description = "Venue found",
					content = @Content(schema = @Schema(implementation = VenueResponse.class))
			),
			@ApiResponse(responseCode = "400", description = "Invalid venue ID", content = @Content),
			@ApiResponse(responseCode = "404", description = "Venue not found", content = @Content)
	})
	@GetMapping("/{id}")
	public ResponseEntity<VenueResponse> getVenueById(
			@Min(value = 1, message = "Event ID must be at least 1") @PathVariable long id
	) {
		return ResponseEntity.ok(venueService.getVenueById(id));
	}

	@Operation(
			summary = "Get all venues",
			description = "Retrieves all venues with pagination support"
	)
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Venues retrieved successfully")
	})
	@GetMapping
	public ResponseEntity<Page<VenueResponse>> getAllVenue(
			Pageable pageable
	) {
		return ResponseEntity.ok(
				venueService.getAllVenues(pageable)
		);
	}

	@Operation(
			summary = "Get venues by city",
			description = "Retrieves all venues located in a specific city with pagination support"
	)
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Venues retrieved successfully")
	})
	@GetMapping("/city/{city}")
	public ResponseEntity<Page<VenueResponse>> getVenuesByCity(
			@PathVariable String city,
			Pageable pageable
	) {
		return ResponseEntity.ok(venueService.getVenuesByCity(city, pageable));
	}

	@Operation(
			summary = "Update venue",
			description = "Updates an existing venue's details"
	)
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Venue updated successfully"),
			@ApiResponse(responseCode = "400", description = "Invalid request data", content = @Content),
			@ApiResponse(responseCode = "404", description = "Venue not found", content = @Content)
	})
	@PutMapping("/{id}")
	public ResponseEntity<VenueResponse> updateVenueById(
			@Min(value = 1, message = "Venue ID must at least 1") @PathVariable long id,
			@Valid @RequestBody CreateVenueRequest request
	) {
		return ResponseEntity.ok(venueService.updateVenueById(id, request));
	}

	@Operation(
			summary = "Delete venue",
			description = "Permanently deletes a venue. Venues with associated events cannot be deleted - delete or reassign the events first."
	)
	@ApiResponses(value = {
			@ApiResponse(responseCode = "204", description = "Venue deleted successfully"),
			@ApiResponse(responseCode = "400", description = "Invalid venue ID", content = @Content),
			@ApiResponse(responseCode = "404", description = "Venue not found", content = @Content),
			@ApiResponse(responseCode = "409", description = "Venue has associated events and cannot be deleted", content = @Content)
	})
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteVenueById(
			@Min(value = 1, message = "Venue ID must be at least 1") @PathVariable long id
	) {
		venueService.deleteVenueById(id);

		return ResponseEntity.noContent().build();
	}
}
