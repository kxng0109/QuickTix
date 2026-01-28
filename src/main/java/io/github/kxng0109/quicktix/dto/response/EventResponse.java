package io.github.kxng0109.quicktix.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;

@Builder
@Schema(description = "Event details response")
public record EventResponse(
		@Schema(description = "Unique event identifier", example = "1")
		Long id,

		@Schema(description = "Name of the event", example = "Summer Music Festival 2026")
		String name,

		@Schema(description = "Detailed description of the event", example = "A three-day music festival featuring top artists.")
		String description,

		@Schema(description = "Name of the venue hosting the event", example = "Grand Arena")
		String venueName,

		@Schema(description = "Price per ticket", example = "15000.00")
		BigDecimal ticketPrice,

		@Schema(
				description = "Current status of the event",
				example = "Upcoming",
				allowableValues = {"Upcoming", "Ongoing", "Completed", "Cancelled"}
		)
		String status,

		@Schema(description = "Number of seats currently available for booking", example = "4500")
		Integer availableSeats,

		@Schema(description = "Event start date and time", example = "2026-06-15T18:00:00Z")
		Instant eventStartDateTime,

		@Schema(description = "Event end date and time", example = "2026-06-15T23:00:00Z")
		Instant eventEndDateTime
) {
}
