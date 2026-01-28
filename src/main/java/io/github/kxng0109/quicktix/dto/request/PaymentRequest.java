package io.github.kxng0109.quicktix.dto.request;

import io.github.kxng0109.quicktix.enums.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
@Schema(description = "Request payload for initializing a payment")
public record PaymentRequest(
		@Schema(
				description = "ID of the booking to pay for",
				example = "1",
				requiredMode = Schema.RequiredMode.REQUIRED,
				minimum = "1"
		)
		@NotNull(message = "Booking ID is required")
		@Positive(message = "Booking ID must be positive")
		Long bookingId,

		@Schema(
				description = "Payment amount. Must exactly match the booking total amount.",
				example = "45000.00",
				requiredMode = Schema.RequiredMode.REQUIRED
		)
		@NotNull(message = "Amount is required")
		@Positive(message = "Amount must be positive")
		BigDecimal amount,

		@Schema(
				description = "Payment method to use",
				example = "CREDIT_CARD",
				requiredMode = Schema.RequiredMode.REQUIRED,
				allowableValues = {"CREDIT_CARD", "DEBIT_CARD", "BANK_TRANSFER", "MOBILE_MONEY"}
		)
		@NotNull(message = "Payment method is required")
		PaymentMethod paymentMethod
) {
}
