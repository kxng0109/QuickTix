package io.github.kxng0109.quicktix.controller;

import io.github.kxng0109.quicktix.dto.request.HoldSeatsRequest;
import io.github.kxng0109.quicktix.dto.response.SeatResponse;
import io.github.kxng0109.quicktix.service.SeatService;
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
public class SeatController {

	private final SeatService seatService;

	@PostMapping("/hold")
	public ResponseEntity<List<SeatResponse>> holdSeats(
			@Valid @RequestBody HoldSeatsRequest request
	) {
		return new ResponseEntity<>(seatService.holdSeats(request), HttpStatus.CREATED);
	}

	@PostMapping("/release")
	public ResponseEntity<Void> releaseSeats(
			@Valid @RequestBody HoldSeatsRequest request
	) {
		seatService.releaseSeats(request);
		return ResponseEntity.noContent().build();
	}
}
