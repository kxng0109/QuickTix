package io.github.kxng0109.quicktix.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Builder;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.Instant;

@Builder
@Schema(description = "Request payload for creating or updating an event")
public record CreateEventRequest(
		@Schema(
				description = "Name of the event",
				example = "Summer Music Festival 2026",
				requiredMode = Schema.RequiredMode.REQUIRED
		)
		@NotBlank(message = "Event name can't be blank.")
		String name,

		@Schema(
				description = "Detailed description of the event",
				example = "A three-day music festival featuring top artists from around the world.",
				requiredMode = Schema.RequiredMode.REQUIRED
		)
		@NotBlank(message = "Event description can't be blank.")
		String description,

		@Schema(
				description = "ID of the venue where the event will be held",
				example = "1",
				requiredMode = Schema.RequiredMode.REQUIRED,
				minimum = "1"
		)
		@NotNull(message = "Event venue ID can't be blank.")
		@Positive(message = "Venue ID must be greater than 0")
		Long venueId,

		@Schema(
				description = "Start date and time of the event in ISO 8601 format. Must be in the future or present.",
				example = "2026-06-15T18:00:00Z",
				requiredMode = Schema.RequiredMode.REQUIRED,
				format = "date-time"
		)
		@NotNull(message = "Event start date time must not be blank")
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
		@FutureOrPresent(message = "Event start date time must be in the future or present.")
		Instant eventStartDateTime,

		@Schema(
				description = "End date and time of the event in ISO 8601 format. Must be after the start time.",
				example = "2026-06-15T23:00:00Z",
				requiredMode = Schema.RequiredMode.REQUIRED,
				format = "date-time"
		)
		@NotNull(message = "Event end date time must not be blank")
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
		@Future(message = "Event end date time must be in the future.")
		Instant eventEndDateTime,

		@Schema(
				description = "Price per ticket in the local currency",
				example = "15000.00",
				requiredMode = Schema.RequiredMode.REQUIRED,
				minimum = "0.01"
		)
		@NotNull(message = "Ticket price must not be blank")
		@DecimalMin(value = "0.01", message = "Ticket price must be greater than 0.01")
		@Digits(integer = 6, fraction = 2, message = "Ticket price must be in the format '999999.99'")
		BigDecimal ticketPrice,

		@Schema(
				description = "Total number of seats to generate for this event. Cannot be changed after creation.",
				example = "5000",
				requiredMode = Schema.RequiredMode.REQUIRED,
				minimum = "0"
		)
		@NotNull(message = "Number of seats can't be blank")
		@PositiveOrZero(message = "Number of seats must be 0 or greater.")
		Integer numberOfSeats
) {
	@AssertTrue(message = "Start date time must be before end date time")
	@Schema(hidden = true)
	public boolean isDateRangeValid() {
		if (eventStartDateTime == null || eventEndDateTime == null)
			return true; //We want the @NotNull annotation to handle it
		return eventStartDateTime.isBefore(eventEndDateTime);
	}
}
