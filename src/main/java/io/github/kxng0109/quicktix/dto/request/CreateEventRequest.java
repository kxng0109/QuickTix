package io.github.kxng0109.quicktix.dto.request;

import jakarta.validation.constraints.*;
import lombok.Builder;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.Instant;

@Builder
public record CreateEventRequest(
        @NotBlank(message = "Event name can't be blank.")
        String name,

        @NotBlank(message = "Event description can't be blank.")
        String description,

        @NotNull(message = "Event venue ID can't be blank.")
        @Positive(message = "Venue ID must be greater than 0")
        Long venueId,

        @NotNull(message = "Event date time must not be blank")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        @FutureOrPresent(message = "Event date time must be in the future or present.")
        Instant eventDateTime,

        @NotNull(message = "Ticket price must not be blank")
        @DecimalMin(value = "0.01", message = "Ticket price must be greater than 0.01")
        @Digits(integer = 6, fraction = 2, message = "Ticket price must be in the format '999999.99'")
        BigDecimal ticketPrice,

        @NotNull(message = "Number of seats can't be blank")
        @PositiveOrZero(message = "Number of seats must be 0 or greater.")
        Integer numberOfSeats
) {
}
