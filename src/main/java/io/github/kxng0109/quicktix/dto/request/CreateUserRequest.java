package io.github.kxng0109.quicktix.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
@Schema(description = "Request payload for creating or updating a user")
public record CreateUserRequest(
		@Schema(
				description = "User's first name",
				example = "John",
				requiredMode = Schema.RequiredMode.REQUIRED,
				maxLength = 64
		)
		@NotBlank(message = "First name can't be blank")
		@Size(max = 64, message = "First name can't be more than 64 characters")
		String firstName,

		@Schema(
				description = "User's last name",
				example = "Doe",
				requiredMode = Schema.RequiredMode.REQUIRED,
				maxLength = 64
		)
		@NotBlank(message = "Last name can't be blank")
		@Size(max = 64, message = "Last name can't be more than 64 characters")
		String lastName,

		@Schema(
				description = "User's email address. Must be unique across all users.",
				example = "john.doe@example.com",
				requiredMode = Schema.RequiredMode.REQUIRED,
				format = "email"
		)
		@NotBlank(message = "Email can't be blank")
		@Email(message = "Email must be valid")
		String email,

		@Schema(
				description = "User's phone number in international format",
				example = "+2341234567890",
				requiredMode = Schema.RequiredMode.NOT_REQUIRED,
				maxLength = 15
		)
		@Size(max = 15, message = "Phone number must be at most 15 characters")
		String phoneNumber
) {
}
