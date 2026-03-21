package io.github.kxng0109.quicktix.security;

import io.github.kxng0109.quicktix.dto.exception.ErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.OffsetDateTime;

@Component
@RequiredArgsConstructor
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

	private final ObjectMapper objectMapper;

	/**
	 * Handles an access denied failure.
	 *
	 * @param request               that resulted in an <code>AccessDeniedException</code>
	 * @param response              so that the user agent can be advised of the failure
	 * @param accessDeniedException that caused the invocation
	 * @throws IOException      in the event of an IOException
	 * @throws ServletException in the event of a ServletException
	 */
	@Override
	public void handle(
			HttpServletRequest request,
			HttpServletResponse response,
			AccessDeniedException accessDeniedException
	) throws IOException, ServletException {
		HttpStatus status = HttpStatus.FORBIDDEN;

		ErrorResponse errorResponse = ErrorResponse.builder()
		                                           .timestamp(OffsetDateTime.now())
		                                           .statusCode(status.value())
		                                           .error(status.getReasonPhrase())
		                                           .message(
				                                           "Access denied. You do not have permission to access this resource.")
		                                           .path(request.getRequestURI())
		                                           .build();

		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		objectMapper.writeValue(response.getOutputStream(), errorResponse);
	}
}
