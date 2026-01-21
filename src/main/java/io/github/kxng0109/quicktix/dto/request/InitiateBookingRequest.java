package io.github.kxng0109.quicktix.dto.request;

import io.github.kxng0109.quicktix.entity.Payment;
import io.github.kxng0109.quicktix.enums.BookingStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
public record InitiateBookingRequest(
        Long userId,
        Long eventId,
        List<Long> seats,
        Payment payment,
        BookingStatus status,
        BigDecimal totalAmount
) {
}
