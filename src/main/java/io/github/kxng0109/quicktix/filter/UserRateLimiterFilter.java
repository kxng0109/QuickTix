package io.github.kxng0109.quicktix.filter;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * A servlet filter implementation for rate limiting incoming requests
 * based on authenticated user identity using a token bucket algorithm.
 *
 * <p>This filter intercepts HTTP requests to enforce rate-limiting logic for
 * authenticated users, identified by their email address. It ensures each user
 * adheres to the configured token consumption rules to prevent excessive server
 * load or abuse of resources. Unauthenticated or anonymous users are bypassed
 * and allowed to proceed without rate limiting. If the rate limit is exceeded,
 * the filter stops the request and responds with HTTP status {@code 429 Too Many Requests},
 * including headers indicating remaining tokens and retry timing.
 *
 * <p>The rate-limiting mechanism is backed by a {@link ProxyManager} for distributed
 * token bucket management and a {@link BucketConfiguration} to control bucket behavior
 * (e.g., refill rate and capacity). This enables centralized control, enhancing system scalability
 * and resilience.
 *
 * <p><strong>Headers managed by this filter:</strong>
 * <ul>
 *   <li>{@code X-Rate-Limit-Remaining} - Indicates the number of remaining tokens in the
 *   user's bucket for the current period.</li>
 *   <li>{@code X-Rate-Limit-Retry-After-Seconds} - Specifies the wait time in seconds
 *   before the user can issue more requests when the limit is exceeded.</li>
 * </ul>
 *
 * @see ProxyManager
 * @see Bucket
 * @see BucketConfiguration
 */
@Slf4j
@Component
public class UserRateLimiterFilter extends OncePerRequestFilter {

	private final ProxyManager<byte[]> proxyManager;
	private final Supplier<BucketConfiguration> bucketConfiguration;
	private final ObjectMapper objectMapper;

	public UserRateLimiterFilter(
			ProxyManager<byte[]> proxyManager,
			@Qualifier("userBucketConfiguration") Supplier<BucketConfiguration> bucketConfiguration,
			ObjectMapper objectMapper) {
		this.proxyManager = proxyManager;
		this.bucketConfiguration = bucketConfiguration;
		this.objectMapper = objectMapper;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain
	) throws ServletException, IOException {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (
				auth == null
						|| !auth.isAuthenticated()
						|| Objects.equals(auth.getPrincipal(), "anonymousUser")
						|| auth.getAuthorities().isEmpty()
		) {
			filterChain.doFilter(request, response);
			return;
		}

		String userEmail = auth.getName();
		byte[] bucketKey = ("rate_limiter:" + userEmail).getBytes();
		Bucket bucket = proxyManager.builder().build(bucketKey, bucketConfiguration);

		ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

		if (probe.isConsumed()) {
			response.setHeader(
					"X-Rate-Limit-Remaining",
					String.valueOf(probe.getRemainingTokens())
			);
			filterChain.doFilter(request, response);
		} else {
			long timeLeft = probe.getNanosToWaitForRefill();
			int timeLeftInSeconds = (int) (timeLeft / 1_000_000_000);

			log.warn(
					"Rate limit exceeded for user with email: {}. Retry after {} seconds.",
					userEmail,
					timeLeftInSeconds
			);

			response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
			response.setHeader(
					"X-Rate-Limit-Retry-After-Seconds",
					String.valueOf(timeLeftInSeconds)
			);
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);

			Map<String, Object> errorDetails = new LinkedHashMap<>();
			errorDetails.put("timestamp", OffsetDateTime.now().toString());
			errorDetails.put("statusCode", HttpStatus.TOO_MANY_REQUESTS.value());
			errorDetails.put("error", HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase());
			errorDetails.put("message",
			                 String.format("Too many requests. Try again in %d seconds.", timeLeftInSeconds)
			);
			errorDetails.put("path", request.getRequestURI());

			String jsonPayload = objectMapper.writeValueAsString(errorDetails);
			response.getWriter().write(jsonPayload);
		}

	}
}
