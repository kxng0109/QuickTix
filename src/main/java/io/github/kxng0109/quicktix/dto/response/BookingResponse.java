package io.github.kxng0109.quicktix.dto.response;

import io.github.kxng0109.quicktix.entity.Seat;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Builder
public record BookingResponse(
        Long id,
        String bookingReference,
        String eventName,
        Instant eventDateTime,
        List<Seat> seats,
        String status,
        BigDecimal totalAmount,
        Instant createdAt
) {
}
