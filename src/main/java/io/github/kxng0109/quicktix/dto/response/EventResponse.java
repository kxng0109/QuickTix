package io.github.kxng0109.quicktix.dto.response;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record EventResponse(
        Long id,
        String name,
        String description,
        String venueName,
        BigDecimal ticketPrice,
        String status,
        Integer availableSeats
) {
}
