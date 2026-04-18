package io.github.kxng0109.quicktix.filter;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A servlet filter implementation for rate limiting incoming requests
 * based on client IP using token bucket algorithm.
 *
 * <p>This filter is responsible for intercepting HTTP requests and applying
 * rate-limiting logic to control the frequency of client requests. It ensures
 * that each client (identified by their IP address) adheres to the configured
 * token-consumption rules. If the consumption limit is exceeded, the response
 * is returned with HTTP status {@code 429 Too Many Requests}, along with
 * appropriate headers and a JSON payload informing the client of retry timing.
 *
 * <p>The rate-limiting mechanism is backed by a {@link ProxyManager} and
 * {@link BucketConfiguration} to manage token bucket state and configuration,
 * respectively.
 *
 * <p><strong>Headers managed by this filter:</strong>
 * <ul>
 *   <li>{@code X-Rate-Limit-Remaining} - Indicates the number of remaining
 *   tokens in the bucket for the current period.</li>
 *   <li>{@code X-Rate-Limit-Retry-After-Seconds} - Specifies the time in
 *   seconds the client must wait before retrying when the rate limit is hit.</li>
 * </ul>
 *
 * @see ProxyManager
 * @see Bucket
 * @see BucketConfiguration
 */
@RequiredArgsConstructor
@Slf4j
public class IPRateLimiterFilter extends OncePerRequestFilter {

	private final ProxyManager<byte[]> proxyManager;
	private final Supplier<BucketConfiguration> bucketConfiguration;
	private final ObjectMapper objectMapper;

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain
	) throws ServletException, IOException {

		String clientIp = request.getRemoteAddr();
		byte[] bucketKey = ("rate_limiter:" + clientIp).getBytes();

		Bucket bucket = proxyManager.builder().build(bucketKey, bucketConfiguration);

		ConsumptionProbe consumptionProbe = bucket.tryConsumeAndReturnRemaining(1);
		if (consumptionProbe.isConsumed()) {
			response.setHeader(
					"X-Rate-Limit-Remaining",
					String.valueOf(consumptionProbe.getRemainingTokens())
			);
			filterChain.doFilter(request, response);
		} else {
			long timeLeft = consumptionProbe.getNanosToWaitForRefill();
			int timeLeftInSeconds = (int) (timeLeft / 1_000_000_000);

			log.warn(
					"Rate limit exceeded for IP: {}. Retry after {} seconds.",
					clientIp,
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
