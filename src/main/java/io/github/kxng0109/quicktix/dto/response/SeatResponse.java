package io.github.kxng0109.quicktix.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "Seat details response")
public record SeatResponse(
		@Schema(description = "Unique seat identifier", example = "1")
		Long id,

		@Schema(description = "Seat number within the row", example = "15")
		Integer seatNumber,

		@Schema(description = "Row identifier", example = "A")
		String rowName,

		@Schema(
				description = "Current status of the seat",
				example = "Available",
				allowableValues = {"Available", "Held", "Booked"}
		)
		String status
) {
}
