package io.github.kxng0109.quicktix.dto.response;

import lombok.Builder;

@Builder
public record SeatResponse(
        Long id,
        Integer seatNumber,
        String rowName,
        String status
) {
}
