package io.github.kxng0109.quicktix.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;

@Builder
public record PaymentResponse(
        Long paymentId,
        BigDecimal amount,
        String status,
        String paymentMethod,
        Instant paidAt
) {
}
