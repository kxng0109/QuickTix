package io.github.kxng0109.quicktix.dto.request;

import jakarta.validation.constraints.*;
import lombok.Builder;

@Builder
public record RowRequest(
		@NotEmpty(message = "Row name required")
		String name,

		@NotNull(message = "Row order is required")
		@PositiveOrZero(message = "Row order can not be negative")
		Integer rowOrder,

		@NotNull(message = "Number of seats can't be less than 1")
		Integer numberOfSeats
) {
}
