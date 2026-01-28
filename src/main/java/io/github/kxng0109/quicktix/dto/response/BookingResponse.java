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
        Instant eventStartDateTime,
		Instant eventEndDateTime,
        List<SeatResponse> seats,
        String status,
        BigDecimal totalAmount,
        Instant createdAt
) {
}
