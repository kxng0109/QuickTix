package io.github.kxng0109.quicktix.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public record SectionRequest(
		@NotEmpty(message = "Section name can't be empty")
		String name,

		@NotEmpty(message = "Section description required")
		String description,

		@NotNull(message = "Section capacity is required.")
		@Min(value = 1, message = "Minimum capacity is 1")
		long capacity,

		@NotNull(message = "Ticket price must not be blank")
		@DecimalMin(value = "0.01", message = "Ticket price must be greater than 0.01")
		@Digits(integer = 6, fraction = 2, message = "Ticket price must be in the format '999999.99'")
		BigDecimal basePrice,

		@NotEmpty(message = "Rows required")
		@Size(min = 1, message = "Rows must be at least 1")
		List<RowRequest> rows
) {
}
