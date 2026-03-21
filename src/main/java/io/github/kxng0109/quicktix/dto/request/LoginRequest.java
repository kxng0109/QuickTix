package io.github.kxng0109.quicktix.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
@Schema(description = "Request payload for user login")
public record LoginRequest (
		@Schema(
				description = "User's email address",
				example = "john.doe@example.com",
				requiredMode = Schema.RequiredMode.REQUIRED,
				format = "email"
		)
		@NotBlank(message = "Email can't be blank")
		@Email(message = "Email must be valid")
		String email,

		@Schema(
				description = "User's password",
				example = "SecureP@ss123",
				requiredMode = Schema.RequiredMode.REQUIRED
		)
		@NotBlank(message = "Password can't be blank")
		String password
){
}
