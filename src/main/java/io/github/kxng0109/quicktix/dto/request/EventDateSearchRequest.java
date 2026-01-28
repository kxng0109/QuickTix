package io.github.kxng0109.quicktix.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;

@Schema(description = "Request parameters for searching events within a date range")
public record EventDateSearchRequest(
		@Schema(
				description = "Start of the date range in ISO 8601 format",
				example = "2026-01-01T00:00:00Z",
				requiredMode = Schema.RequiredMode.REQUIRED,
				format = "date-time"
		)
		@NotNull(message = "Start date can't be null")
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
		Instant startDate,

		@Schema(
				description = "End of the date range in ISO 8601 format. Must be after start date.",
				example = "2026-12-31T23:59:59Z",
				requiredMode = Schema.RequiredMode.REQUIRED,
				format = "date-time"
		)
		@NotNull(message = "End date can't be null")
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
		Instant endDate
) {
	@AssertTrue(message = "Start date must be before end date")
	@Schema(hidden = true)
	public boolean isDateRangeValid() {
		if (startDate == null || endDate == null) return true; //We want the @NotNull annotation to handle it
		return startDate.isBefore(endDate);
	}
}
