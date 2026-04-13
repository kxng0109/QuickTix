package io.github.kxng0109.quicktix.controller;

import io.github.kxng0109.quicktix.dto.request.PaymentRequest;
import io.github.kxng0109.quicktix.dto.response.PaymentResponse;
import io.github.kxng0109.quicktix.entity.User;
import io.github.kxng0109.quicktix.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Validated
@Tag(name = "Payments", description = "Payment initialization, verification, and refunds")
public class PaymentController {

	private final PaymentService paymentService;

	@Operation(
			summary = "Initialize payment",
			description = """
					Initializes a payment for a pending booking. Returns a transaction reference to be used with the payment gateway.
					
					**Prerequisites:**
					- Booking must exist and be in PENDING status
					- Payment amount must exactly match the booking total
					
					**Payment flow:**
					1. Call this endpoint to initialize payment and get transaction reference
					2. User completes payment through the external gateway
					3. On successful verification, booking is automatically confirmed
					"""
	)
	@ApiResponses(value = {
			@ApiResponse(
					responseCode = "201",
					description = "Payment initialized successfully",
					content = @Content(schema = @Schema(implementation = PaymentResponse.class))
			),
			@ApiResponse(
					responseCode = "400",
					description = "Booking not in PENDING status or amount mismatch",
					content = @Content(
							examples = @ExampleObject(
									value = """
											{
											    "statusCode": 400,
											    "message": "Payment amount mismatch",
											    "path": "/api/v1/payments/initialize",
											    "timestamp": "2026-01-28T12:00:00Z"
											}
											"""
							)
					)
			),
			@ApiResponse(responseCode = "404", description = "Booking not found", content = @Content)
	})
	@PreAuthorize("hasAnyRole('ADMIN', 'USER')")
	@PostMapping("/initialize")
	public ResponseEntity<PaymentResponse> initializePayment(
			@Valid @RequestBody PaymentRequest request,
			@Parameter(
					description = "Client-generated idempotency key used to safely retry payment initialization",
					example = "4f6a3cc0-5243-4ae2-a7f6-37d0ec4ad877",
					required = true
			)
			@RequestHeader(name = "Idempotency-Key")
			@NotEmpty(message = "Idempotency key is required")
			@Size(min = 8, message = "Idempotency key must be at least 8 characters")
			String idempotencyKey,
			@AuthenticationPrincipal User currentUser
	) {
		return new ResponseEntity<>(
				paymentService.initializePayment(
						request,
						idempotencyKey,
						currentUser
				),
				HttpStatus.CREATED
		);
	}
}
