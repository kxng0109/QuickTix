package io.github.kxng0109.quicktix.controller;

import io.github.kxng0109.quicktix.dto.request.InitiateBookingRequest;
import io.github.kxng0109.quicktix.dto.response.BookingResponse;
import io.github.kxng0109.quicktix.dto.response.PaymentResponse;
import io.github.kxng0109.quicktix.service.BookingService;
import io.github.kxng0109.quicktix.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@Validated
public class BookingController {

	private final BookingService bookingService;
	private final PaymentService paymentService;

	@PostMapping
	public ResponseEntity<BookingResponse> createBooking(
			@Valid @RequestBody InitiateBookingRequest request
	) {
		return new ResponseEntity<>(
				bookingService.createPendingBooking(request),
				HttpStatus.CREATED
		);
	}

	@GetMapping("/{id}")
	public ResponseEntity<BookingResponse> getBookingById(
			@Min(value = 1, message = "Booking ID must be 1 or greater") @PathVariable long id
	) {
		return ResponseEntity.ok(bookingService.getBookingById(id));
	}

	@GetMapping("/reference/{reference}")
	public ResponseEntity<BookingResponse> getBookingByReference(
			@NotBlank(message = "Transaction reference is required") @PathVariable String reference
	) {
		return ResponseEntity.ok(bookingService.getBookingByReference(reference));
	}

	@GetMapping("/{id}/payment")
	public ResponseEntity<PaymentResponse> getPaymentByBookingId(
			@Min(value = 1, message = "ID must be 1 or greater") @PathVariable long id
	) {
		return ResponseEntity.ok(paymentService.getPaymentByBookingId(id));
	}

	@PatchMapping("/{id}/cancel")
	public ResponseEntity<Void> cancelBookingById(
			@Min(value = 1, message = "ID must be 1 or greater") @PathVariable long id
	) {
		bookingService.cancelBooking(id);
		return ResponseEntity.noContent().build();
	}

	@PatchMapping("/{id}/refund")
	public ResponseEntity<Void> refundPayment(
			@Min(value = 1, message = "ID must be 1 or greater") @PathVariable long id
	) {
		paymentService.refundPayment(id);
		return ResponseEntity.noContent().build();
	}
}
