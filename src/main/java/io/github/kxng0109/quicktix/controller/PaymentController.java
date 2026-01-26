package io.github.kxng0109.quicktix.controller;

import io.github.kxng0109.quicktix.dto.request.PaymentRequest;
import io.github.kxng0109.quicktix.dto.response.PaymentResponse;
import io.github.kxng0109.quicktix.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Validated
public class PaymentController {

	private final PaymentService paymentService;

	@PostMapping("/initialize")
	public ResponseEntity<PaymentResponse> initializePayment(
			@Valid @RequestBody PaymentRequest request
	) {
		return new ResponseEntity<>(
				paymentService.initializePayment(request),
				HttpStatus.CREATED
		);
	}

	@GetMapping("/verify/{transactionReference}")
	public ResponseEntity<PaymentResponse> verifyPayment(
			@NotBlank(message = "Transaction reference is required") @PathVariable String transactionReference
	) {
		return ResponseEntity.ok(paymentService.verifyPayment(transactionReference));
	}
}
