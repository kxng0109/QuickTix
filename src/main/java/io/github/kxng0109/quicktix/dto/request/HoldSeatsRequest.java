package io.github.kxng0109.quicktix.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;

import java.util.List;

@Builder
public record HoldSeatsRequest(
        @NotNull(message = "Event ID can't be null")
        @Positive(message = "Event ID can't be negative or zero")
        Long eventId,

        @NotNull(message = "User ID can't be null")
        @Positive(message = "User ID can't be negative or zero")
        Long userId,

        @NotNull(message = "Seat ID's can't be null")
        List<@NotNull(message = "Seat ID can't be null") @Positive(message = "Seat ID must be positive") Long> seatIds
) {
}
