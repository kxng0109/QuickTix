package io.github.kxng0109.quicktix.security;

import io.github.kxng0109.quicktix.dto.exception.ErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.OffsetDateTime;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final ObjectMapper objectMapper;

	/**
	 * Commences an authentication scheme.
	 * <p>
	 * <code>ExceptionTranslationFilter</code> will populate the <code>HttpSession</code>
	 * attribute named
	 * <code>AbstractAuthenticationProcessingFilter.SPRING_SECURITY_SAVED_REQUEST_KEY</code>
	 * with the requested target URL before calling this method.
	 * <p>
	 * Implementations should modify the headers on the <code>ServletResponse</code> as
	 * necessary to commence the authentication process.
	 *
	 * @param request       that resulted in an <code>AuthenticationException</code>
	 * @param response      so that the user agent can begin authentication
	 * @param authException that caused the invocation
	 */
	@Override
	public void commence(
			HttpServletRequest request,
			HttpServletResponse response,
			AuthenticationException authException
	) throws IOException {
		HttpStatus status = HttpStatus.UNAUTHORIZED;

		ErrorResponse errorResponse = ErrorResponse.builder()
		                                           .timestamp(OffsetDateTime.now())
		                                           .statusCode(status.value())
		                                           .error(status.getReasonPhrase())
		                                           .message("Authentication required. Please provide a valid token.")
		                                           .path(request.getRequestURI())
		                                           .build();

		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);

		objectMapper.writeValue(response.getOutputStream(), errorResponse);
	}
}
