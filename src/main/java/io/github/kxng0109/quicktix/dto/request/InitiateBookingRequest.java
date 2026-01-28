package io.github.kxng0109.quicktix.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
@Schema(description = "Request payload for creating a new booking")
public record InitiateBookingRequest(
		@Schema(
				description = "ID of the user making the booking",
				example = "1",
				requiredMode = Schema.RequiredMode.REQUIRED,
				minimum = "1"
		)
		@NotNull(message = "User ID is required")
		@Positive(message = "User ID must be positive")
		Long userId,

		@Schema(
				description = "ID of the event being booked",
				example = "1",
				requiredMode = Schema.RequiredMode.REQUIRED,
				minimum = "1"
		)
		@NotNull(message = "Event ID is required")
		@Positive(message = "Event ID must be positive")
		Long eventId,

		@Schema(
				description = "List of seat IDs to book. All seats must be currently held by the user.",
				example = "[1, 2, 3]",
				requiredMode = Schema.RequiredMode.REQUIRED
		)
		@NotNull(message = "Seats are required")
		List<@NotNull(message = "A seat is required") Long> seats,

		@Schema(
				description = "Total amount for the booking. Must match seat count multiplied by ticket price.",
				example = "45000.00",
				requiredMode = Schema.RequiredMode.REQUIRED
		)
		BigDecimal totalAmount
) {
}
