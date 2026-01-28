package io.github.kxng0109.quicktix.controller;

import io.github.kxng0109.quicktix.dto.request.InitiateBookingRequest;
import io.github.kxng0109.quicktix.dto.response.BookingResponse;
import io.github.kxng0109.quicktix.dto.response.PaymentResponse;
import io.github.kxng0109.quicktix.service.BookingService;
import io.github.kxng0109.quicktix.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Bookings", description = "Booking creation, confirmation, and cancellation")
public class BookingController {

	private final BookingService bookingService;
	private final PaymentService paymentService;

	@Operation(
			summary = "Create a pending booking",
			description = """
					Creates a new booking in PENDING status with the specified held seats.
					
					**Prerequisites:**
					- User must have held the seats first using `/api/v1/seats/hold`
					- All seats must be held by the requesting user
					- Total amount must match the calculated price (seat count × ticket price)
					
					**Booking lifecycle:**
					1. `PENDING` - Created, awaiting payment (15 minutes to complete)
					2. `CONFIRMED` - Payment verified successfully
					3. `CANCELLED` - User cancelled or payment failed
					4. `EXPIRED` - No payment received within 15 minutes
					
					**Next step:** Initialize payment using `/api/v1/payments/initialize`
					"""
	)
	@ApiResponses(value = {
			@ApiResponse(
					responseCode = "201",
					description = "Booking created successfully",
					content = @Content(schema = @Schema(implementation = BookingResponse.class))
			),
			@ApiResponse(
					responseCode = "400",
					description = "Seats not held by user or validation failed",
					content = @Content
			),
			@ApiResponse(responseCode = "404", description = "User or event not found", content = @Content)
	})
	@PostMapping
	public ResponseEntity<BookingResponse> createBooking(
			@Valid @RequestBody InitiateBookingRequest request
	) {
		return new ResponseEntity<>(
				bookingService.createPendingBooking(request),
				HttpStatus.CREATED
		);
	}

	@Operation(
			summary = "Get booking by ID",
			description = "Retrieves a booking's details by its unique identifier"
	)
	@ApiResponses(value = {
			@ApiResponse(
					responseCode = "200",
					description = "Booking found",
					content = @Content(schema = @Schema(implementation = BookingResponse.class))
			),
			@ApiResponse(responseCode = "400", description = "Invalid booking ID", content = @Content),
			@ApiResponse(responseCode = "404", description = "Booking not found", content = @Content)
	})
	@GetMapping("/{id}")
	public ResponseEntity<BookingResponse> getBookingById(
			@Min(value = 1, message = "Booking ID must be 1 or greater") @PathVariable long id
	) {
		return ResponseEntity.ok(bookingService.getBookingById(id));
	}

	@Operation(
			summary = "Get booking by reference",
			description = "Retrieves a booking's details by its unique reference code (e.g., QT-ABC123). This is useful for customer support lookups."
	)
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Booking found"),
			@ApiResponse(responseCode = "400", description = "Invalid booking reference", content = @Content),
			@ApiResponse(responseCode = "404", description = "Booking not found", content = @Content)
	})
	@GetMapping("/reference/{reference}")
	public ResponseEntity<BookingResponse> getBookingByReference(
			@NotBlank(message = "Transaction reference is required") @PathVariable String reference
	) {
		return ResponseEntity.ok(bookingService.getBookingByReference(reference));
	}

	@Operation(
			summary = "Get payment for booking",
			description = "Retrieves the payment details associated with a booking"
	)
	@ApiResponses(value = {
			@ApiResponse(
					responseCode = "200",
					description = "Payment found",
					content = @Content(schema = @Schema(implementation = PaymentResponse.class))
			),
			@ApiResponse(responseCode = "400", description = "Invalid booking ID", content = @Content),
			@ApiResponse(responseCode = "404", description = "Booking or payment not found", content = @Content)
	})
	@GetMapping("/{id}/payment")
	public ResponseEntity<PaymentResponse> getPaymentByBookingId(
			@Min(value = 1, message = "ID must be 1 or greater") @PathVariable long id
	) {
		return ResponseEntity.ok(paymentService.getPaymentByBookingId(id));
	}

	@Operation(
			summary = "Cancel booking",
			description = """
					Cancels a pending booking and releases all associated seats.
					
					**Rules:**
					- Only bookings in PENDING status can be cancelled by users
					- Confirmed bookings require contacting support for refunds
					- Cancelled/expired bookings cannot be cancelled again
					
					**Effect:**
					- Booking status changes to CANCELLED
					- All held seats are released back to AVAILABLE
					"""
	)
	@ApiResponses(value = {
			@ApiResponse(responseCode = "204", description = "Booking cancelled successfully"),
			@ApiResponse(responseCode = "400", description = "Booking cannot be cancelled - not in PENDING status", content = @Content),
			@ApiResponse(responseCode = "404", description = "Booking not found", content = @Content)
	})
	@PatchMapping("/{id}/cancel")
	public ResponseEntity<Void> cancelBookingById(
			@Min(value = 1, message = "ID must be 1 or greater") @PathVariable long id
	) {
		bookingService.cancelBooking(id);
		return ResponseEntity.noContent().build();
	}

	@Operation(
			summary = "Refund booking payment",
			description = """
					Initiates a refund for a completed payment and cancels the booking.
					
					**Rules:**
					- Only payments in COMPLETED status can be refunded
					- Already refunded payments will return an error
					- Pending/failed payments cannot be refunded
					
					**Effect:**
					- Payment status changes to REFUNDED
					- Booking status changes to CANCELLED
					- All booked seats are released back to AVAILABLE
					"""
	)
	@ApiResponses(value = {
			@ApiResponse(responseCode = "204", description = "Payment refunded successfully"),
			@ApiResponse(
					responseCode = "400",
					description = "Payment cannot be refunded - not in COMPLETED status",
					content = @Content(
							examples = @ExampleObject(
									value = """
											{
											    "statusCode": 400,
											    "message": "Cannot refund payment if payment was not completed.",
											    "path": "/api/v1/bookings/1/refund",
											    "timestamp": "2026-01-28T12:00:00Z"
											}
											"""
							)
					)
			),
			@ApiResponse(responseCode = "404", description = "Booking or payment not found", content = @Content)
	})
	@PatchMapping("/{id}/refund")
	public ResponseEntity<Void> refundPayment(
			@Min(value = 1, message = "ID must be 1 or greater") @PathVariable long id
	) {
		paymentService.refundPayment(id);
		return ResponseEntity.noContent().build();
	}
}
