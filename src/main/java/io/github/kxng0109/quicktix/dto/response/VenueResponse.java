package io.github.kxng0109.quicktix.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "Venue details response")
public record VenueResponse(
		@Schema(description = "Unique venue identifier", example = "1")
		Long id,

		@Schema(description = "Name of the venue", example = "Grand Arena")
		String name,

		@Schema(description = "Full street address", example = "123 Main Street, Victoria Island")
		String address,

		@Schema(description = "City where the venue is located", example = "Lagos")
		String city,

		@Schema(description = "Maximum seating capacity", example = "50000")
		Integer totalCapacity
) {
}
