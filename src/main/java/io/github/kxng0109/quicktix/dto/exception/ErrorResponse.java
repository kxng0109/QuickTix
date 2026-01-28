package io.github.kxng0109.quicktix.dto.exception;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.OffsetDateTime;

/**
 * Represents an error response from a service.
 *
 * @param timestamp  the timestamp when the error occurred, must not be null
 * @param statusCode the HTTP status code associated with the error, must not be negative
 * @param error      a brief description of the type of error that occurred, must not be blank
 * @param message    a detailed message describing what went wrong, may be null
 * @param path       the URL path where the error occurred, may be null
 */
@Builder
@Schema(description = "Standard error response returned for all API errors")
public record ErrorResponse(
		@Schema(
				description = "Timestamp when the error occurred",
				example = "2026-01-28T12:00:00+01:00"
		)
		OffsetDateTime timestamp,

		@Schema(
				description = "HTTP status code",
				example = "404"
		)
		int statusCode,

		@Schema(
				description = "HTTP status reason phrase",
				example = "Not Found"
		)
		String error,

		@Schema(
				description = "Detailed error message explaining what went wrong",
				example = "User not found with id: 999"
		)
		String message,

		@Schema(
				description = "API endpoint path where the error occurred",
				example = "/api/v1/users/999"
		)
		String path
) {
}