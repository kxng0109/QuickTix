package io.github.kxng0109.quicktix.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
@Schema(description = "Request payload for creating or updating a venue")
public record CreateVenueRequest(
		@Schema(
				description = "Name of the venue",
				example = "Grand Arena",
				requiredMode = Schema.RequiredMode.REQUIRED
		)
		@NotBlank(message = "Venue name can't be blank")
		String name,

		@Schema(
				description = "Full street address of the venue",
				example = "123 Main Street, Victoria Island",
				requiredMode = Schema.RequiredMode.REQUIRED
		)
		@NotBlank(message = "Address can't be blank")
		String address,

		@Schema(
				description = "City where the venue is located",
				example = "Lagos",
				requiredMode = Schema.RequiredMode.REQUIRED
		)
		@NotBlank(message = "City can't be blank")
		String city,

		@Schema(
				description = "Maximum seating capacity of the venue",
				example = "50000",
				requiredMode = Schema.RequiredMode.REQUIRED,
				minimum = "0"
		)
		@NotNull(message = "Venue total capacity can not be blank")
		@Min(value = 0, message = "Venue total capacity must be greater than 0")
		Integer totalCapacity
) {
}
