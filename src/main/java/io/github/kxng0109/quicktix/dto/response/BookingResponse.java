package io.github.kxng0109.quicktix.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Builder
@Schema(description = "Booking details response")
public record BookingResponse(
		@Schema(description = "Unique booking identifier", example = "1")
		Long id,

		@Schema(
				description = "Unique booking reference code for customer support",
				example = "QT-ABC123"
		)
		String bookingReference,

		@Schema(description = "Name of the booked event", example = "Summer Music Festival 2026")
		String eventName,

		@Schema(description = "Event start date and time", example = "2026-06-15T18:00:00Z")
		Instant eventStartDateTime,

		@Schema(description = "Event end date and time", example = "2026-06-15T23:00:00Z")
		Instant eventEndDateTime,

		@Schema(description = "List of booked seats")
		List<SeatResponse> seats,

		@Schema(
				description = "Current status of the booking",
				example = "Pending",
				allowableValues = {"Pending", "Confirmed", "Cancelled", "Expired"}
		)
		String status,

		@Schema(description = "Total amount for the booking", example = "45000.00")
		BigDecimal totalAmount,

		@Schema(description = "Timestamp when the booking was created", example = "2026-01-28T12:00:00Z")
		Instant createdAt
) {
}
