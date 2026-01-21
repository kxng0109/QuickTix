package io.github.kxng0109.quicktix.dto.request;

import io.github.kxng0109.quicktix.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record PaymentRequest(
        @NotNull(message = "Booking ID is required")
        @Positive(message = "Booking ID must be positive")
        Long bookingId,

        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be positive")
        BigDecimal amount,

        @NotNull(message = "Payment method is required")
        PaymentMethod paymentMethod
) {
}
