package io.github.kxng0109.quicktix.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import lombok.Builder;

import java.time.Instant;

/**
 * Represents a request for updating an event.
 * <p>
 * This record is used to encapsulate the necessary details required to update an existing event. It includes the name, description,
 * start and end dates of the event. The `eventStartDateTime` must be in the future or present relative to the current time, while
 * `eventEndDateTime` must be strictly in the future.
 *
 * @param name            the new name for the event
 * @param description     the updated description of the event
 * @param eventStartDateTime  the new start date and time of the event (must be in the future or present)
 * @param eventEndDateTime    the new end date and time of the event (must be strictly in the future)
 */
@Builder
public record UpdateEventRequest(
		String name,
		String description,

		@FutureOrPresent
		Instant eventStartDateTime,

		@Future
		Instant eventEndDateTime
) {
}
