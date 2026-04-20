package io.github.kxng0109.quicktix.filter;

import io.github.kxng0109.quicktix.entity.User;
import io.github.kxng0109.quicktix.exception.JwtExpiredException;
import io.github.kxng0109.quicktix.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * JWT Authentication Filter.
 * <p>
 * This filter intercepts every HTTP request and:
 * 1. Extracts the JWT token from the Authorization header
 * 2. Validates the token
 * 3. Loads the user from the database
 * 4. Sets the authentication in the SecurityContext
 * <p>
 * Once authenticated, the user's identity is available throughout
 * the request via SecurityContextHolder.
 * <p>
 * Extends OncePerRequestFilter to guarantee single execution per request.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtService jwtService;
	private final UserDetailsService userDetailsService;
	private final StringRedisTemplate stringRedisTemplate;
	private final ObjectMapper objectMapper;

	/**
	 * Process each request and authenticate if valid JWT is present.
	 * <p>
	 * Flow:
	 * 1. Check for Authorization header with Bearer token
	 * 2. Extract and validate the token
	 * 3. Load user and set authentication
	 * 4. Continue filter chain
	 */
	@Override
	protected void doFilterInternal(
			@NonNull HttpServletRequest request,
			@NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain
	) throws ServletException, IOException {

		final String authHeader = request.getHeader("Authorization");

		if (authHeader == null || !authHeader.startsWith("Bearer ")) {
			filterChain.doFilter(request, response);
			return;
		}

		// Extract token (remove "Bearer " prefix)
		final String token = authHeader.substring(7);

		if(jwtService.isTokenExpired(token)) throw new JwtExpiredException("Invalid token");

		boolean isBlacklisted = Objects.equals(
				Boolean.TRUE,
				stringRedisTemplate.hasKey("blacklist:" + token)
		);

		if (isBlacklisted) {
			log.warn("Attempted use of blacklisted token.");
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);

			Map<String, Object> errorDetails = new LinkedHashMap<>();
			errorDetails.put("timestamp", OffsetDateTime.now().toString());
			errorDetails.put("statusCode", 401);
			errorDetails.put("error", "Unauthorized");
			errorDetails.put("message", "Token has been invalidated via logout.");
			errorDetails.put("path", request.getRequestURI());

			String jsonPayload = objectMapper.writeValueAsString(errorDetails);
			response.getWriter().write(jsonPayload);

			return;
		}

		// Extract email from token
		final String email = jwtService.extractEmail(token);

		// If email extracted and user not already authenticated
		if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
			User user = (User) userDetailsService.loadUserByUsername(email);

			if(!user.isActive()) {
				stringRedisTemplate.opsForValue()
						.setIfAbsent("blacklist:" + token,
						             OffsetDateTime.now().toString(),
								     Duration.ofSeconds(
											 jwtService.getTokenExpirationInSeconds(token) -
													 (System.currentTimeMillis() / 1000)
								     )
						);
				throw new UsernameNotFoundException(email);
			}

			if (jwtService.isTokenValid(token, user)) {
				UsernamePasswordAuthenticationToken authToken =
						new UsernamePasswordAuthenticationToken(
								user,
								null,
								user.getAuthorities()
						);

				authToken.setDetails(
						new WebAuthenticationDetailsSource().buildDetails(request)
				);

				SecurityContextHolder.getContext().setAuthentication(authToken);

				log.debug("Authenticated user: {} with role: {}", email, user.getRole());
			} else {
				log.debug("Invalid JWT token for user: {}", email);
			}
		}

		filterChain.doFilter(request, response);
	}
}
