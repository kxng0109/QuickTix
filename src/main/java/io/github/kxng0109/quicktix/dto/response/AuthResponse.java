package io.github.kxng0109.quicktix.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * Response returned after successful authentication.
 * <p>
 * Contains the JWT token and metadata about the token.
 */
@Builder
@Schema(description = "Authentication response containing JWT token")
public record AuthResponse(
		@Schema(
				description = "JWT access token. Include this in the Authorization header for authenticated requests.",
				example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"
		)
		String token,

		@Schema(
				description = "Token type. Always 'Bearer' for JWT tokens.",
				example = "Bearer"
		)
		String tokenType,

		@Schema(
				description = "Token validity duration in seconds",
				example = "86400"
		)
		Long expiresIn,

		@Schema(
				description = "Authenticated user's email",
				example = "john.doe@example.com"
		)
		String email,

		@Schema(
				description = "Authenticated user's role",
				example = "USER"
		)
		String role
) {
}
