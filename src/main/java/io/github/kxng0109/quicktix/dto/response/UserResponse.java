package io.github.kxng0109.quicktix.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
@Schema(description = "User details response")
public record UserResponse(
		@Schema(description = "Unique user identifier", example = "1")
		Long id,

		@Schema(description = "User's first name", example = "John")
		String firstName,

		@Schema(description = "User's last name", example = "Doe")
		String lastName,

		@Schema(description = "User's email address", example = "john.doe@example.com")
		String email,

		@Schema(description = "User's phone number", example = "+2341234567890")
		String phoneNumber
) {
}
