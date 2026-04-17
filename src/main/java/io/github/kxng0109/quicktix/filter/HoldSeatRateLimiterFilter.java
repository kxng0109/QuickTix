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

import java.io.IOException;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * A servlet filter implementation for rate limiting "Hold Seat" requests based on user identity.
 *
 * <p>This filter applies a {@code token bucket} algorithm to limit the rate of requests to the
 * {@code /api/v1/seats/hold} endpoint. Requests from authenticated users are identified via their email address,
 * which is used as the key to track and enforce rate-limiting rules. If the consumption limit is exceeded,
 * the response is returned with a status of {@code 429 Too Many Requests}, along with appropriate headers
 * and a JSON payload detailing retry timing.
 *
 * <p>This rate limiter ensures that critical endpoints are protected from abuse or excessive traffic,
 * particularly for reserved resources like seat holds, while allowing legitimate users to proceed within
 * the allowed request thresholds.
 *
 * <p><strong>Headers managed by this filter:</strong>
 * <ul>
 *   <li>{@code X-Rate-Limit-Remaining} - Indicates the number of remaining tokens for the user.</li>
 *   <li>{@code X-Rate-Limit-Retry-After-Seconds} - Specifies the time in seconds the user must wait
 *   before retrying when the rate limit is reached.</li>
 * </ul>
 *
 * @see ProxyManager If using distributed buckets for multi-node deployments.
 * @see BucketConfiguration For configuring token bucket limits and refill rates.
 * @see OncePerRequestFilter For filter lifecycle and Spring integration details.
 */
@Component
@Slf4j
public class HoldSeatRateLimiterFilter extends OncePerRequestFilter {

	private final ProxyManager<byte[]> proxyManager;
	private final Supplier<BucketConfiguration> bucketConfiguration;

	public HoldSeatRateLimiterFilter(
			ProxyManager<byte[]> proxyManager,
			@Qualifier("holdSeatsBucketConfiguration") Supplier<BucketConfiguration> bucketConfiguration
	) {
		this.proxyManager = proxyManager;
		this.bucketConfiguration = bucketConfiguration;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain
	) throws ServletException, IOException {

		if (!Objects.equals(request.getRequestURI(), "/api/v1/seats/hold")) {
			filterChain.doFilter(request, response);
			return;
		}

		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (
				auth == null
						|| !auth.isAuthenticated()
						|| Objects.equals(auth.getPrincipal(), "anonymousUser")
						|| Objects.equals(auth.getName(), "user")
		) {
			filterChain.doFilter(request, response);
			return;
		}

		String userEmail = auth.getName();
		byte[] bucketKey = ("rate_limiter_critical:" + userEmail).getBytes();
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
					"Critical rate limit exceeded for user with email: {}. Retry after {} seconds.",
					userEmail,
					timeLeftInSeconds
			);

			response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
			response.setHeader(
					"X-Rate-Limit-Retry-After-Seconds",
					String.valueOf(timeLeftInSeconds)
			);
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);

			String jsonPayload = String.format(
					"{\"statusCode\": 429, \"message\": \"Too many requests. Try again in %d seconds.\", \"path\": \"%s\"}",
					timeLeftInSeconds, request.getRequestURI()
			);
			response.getWriter().write(jsonPayload);
		}

	}
}
