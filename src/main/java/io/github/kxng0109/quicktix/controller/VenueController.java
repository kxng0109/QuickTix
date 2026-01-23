package io.github.kxng0109.quicktix.controller;

import io.github.kxng0109.quicktix.dto.request.CreateVenueRequest;
import io.github.kxng0109.quicktix.dto.response.VenueResponse;
import io.github.kxng0109.quicktix.service.VenueService;
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
public class VenueController {

	private final VenueService venueService;

	@PostMapping
	public ResponseEntity<VenueResponse> createVenue(@Valid @RequestBody CreateVenueRequest request) {
		return new ResponseEntity<>(venueService.createVenue(request), HttpStatus.CREATED);
	}

	@GetMapping("/{id}")
	public ResponseEntity<VenueResponse> getVenueById(
			@Min(value = 1, message = "Event ID must be at least 1") @PathVariable long id
	) {
		return ResponseEntity.ok(venueService.getVenueById(id));
	}

	@GetMapping
	public ResponseEntity<Page<VenueResponse>> getAllVenue(
			Pageable pageable
	) {
		return ResponseEntity.ok(
				venueService.getAllVenues(pageable)
		);
	}

	@GetMapping("/city/{city}")
	public ResponseEntity<Page<VenueResponse>> getVenuesByCity(
			@PathVariable String city,
			Pageable pageable
	) {
		return ResponseEntity.ok(venueService.getVenuesByCity(city, pageable));
	}

	@PutMapping("/{id}")
	public ResponseEntity<VenueResponse> updateVenueById(
			@Min(value = 1, message = "Venue ID must at least 1") @PathVariable long id,
			@Valid @RequestBody CreateVenueRequest request
	) {
		return ResponseEntity.ok(venueService.updateVenueById(id, request));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteVenueById(
			@Min(value = 1, message = "Venue ID must be at least 1") @PathVariable long id
	) {
		venueService.deleteVenueById(id);

		return ResponseEntity.noContent().build();
	}
}
