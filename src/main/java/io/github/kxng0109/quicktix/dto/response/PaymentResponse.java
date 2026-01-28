package io.github.kxng0109.quicktix.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;

@Builder
@Schema(description = "Payment details response")
public record PaymentResponse(
		@Schema(description = "Unique payment identifier", example = "1")
		Long paymentId,

		@Schema(description = "Payment amount", example = "45000.00")
		BigDecimal amount,

		@Schema(
				description = "Current status of the payment",
				example = "Completed",
				allowableValues = {"Pending", "Completed", "Failed", "Refunded"}
		)
		String status,

		@Schema(
				description = "Payment method used",
				example = "Credit Card",
				allowableValues = {"Credit Card", "Debit Card", "Bank Transfer", "Mobile Money"}
		)
		String paymentMethod,

		@Schema(
				description = "Timestamp when payment was completed. Null if not yet paid.",
				example = "2026-01-28T12:05:00Z",
				nullable = true
		)
		Instant paidAt
) {
}
