package io.github.kxng0109.quicktix.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;

import java.util.List;

@Builder
@Schema(description = "Request payload for holding or releasing seats")
public record HoldSeatsRequest(
		@Schema(
				description = "ID of the event for which seats are being held",
				example = "1",
				requiredMode = Schema.RequiredMode.REQUIRED,
				minimum = "1"
		)
		@NotNull(message = "Event ID can't be null")
		@Positive(message = "Event ID can't be negative or zero")
		Long eventId,

		@Schema(
				description = "ID of the user holding the seats",
				example = "1",
				requiredMode = Schema.RequiredMode.REQUIRED,
				minimum = "1"
		)
		@NotNull(message = "User ID can't be null")
		@Positive(message = "User ID can't be negative or zero")
		Long userId,

		@Schema(
				description = "List of seat IDs to hold or release",
				example = "[1, 2, 3]",
				requiredMode = Schema.RequiredMode.REQUIRED
		)
		@NotNull(message = "Seat ID's can't be null")
		List<@NotNull(message = "Seat ID can't be null") @Positive(message = "Seat ID must be positive") Long> seatIds
) {
}
