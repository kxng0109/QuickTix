package io.github.kxng0109.quicktix.config;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.kxng0109.quicktix.filter.IPRateLimiterFilter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Supplier;

/**
 * Configuration class for registering and configuring the {@link IPRateLimiterFilter}
 * as a servlet filter to enforce API request rate limits.
 *
 * <p>This class defines a {@link FilterRegistrationBean} to register the
 * {@link IPRateLimiterFilter} with specific URL patterns and execution order.
 * The filter applies rate-limiting mechanisms to optimize server resource usage
 * and prevent abuse by limiting the rate of incoming requests to protected
 * endpoints.
 * </p>
 *
 * <p><strong>Key Characteristics:</strong>
 * <ul>
 *   <li>The filter is applied specifically to endpoints matching the pattern
 *       {@code /api/v1/*}, excluding auxiliary endpoints like API documentation
 *       (Swagger) and actuator monitoring endpoints.</li>
 *   <li>The execution order is set to {@code 1}, ensuring that the filter is
 *       invoked early in the servlet filter chain to reject excessive traffic
 *       before further layers (e.g., authentication) are engaged.</li>
 * </ul>
 * </p>
 *
 * <p>The actual rate-limiting logic is implemented in {@link IPRateLimiterFilter},
 * which leverages the token bucket algorithm backed by {@link ProxyManager} and
 * {@link BucketConfiguration} provided as dependencies.
 * </p>
 *
 * @see IPRateLimiterFilter
 * @see ProxyManager
 * @see BucketConfiguration
 */
@Configuration
public class FilterConfig {

	@Bean
	public FilterRegistrationBean<IPRateLimiterFilter> rateLimiterFilter(
			ProxyManager<byte[]> proxyManager,
			@Qualifier("ipBucketConfiguration") Supplier<BucketConfiguration> bucketConfiguration
	) {
		FilterRegistrationBean<IPRateLimiterFilter> registrationBean = new FilterRegistrationBean<>();
		registrationBean.setFilter(new IPRateLimiterFilter(proxyManager, bucketConfiguration));
		// Target API endpoints ONLY (Ignore Swagger, Actuator, etc.)
		registrationBean.addUrlPatterns("/api/v1/*");
		// Set the order. Setting it to 1 ensures it runs very early in the chain,
		// catching spam before it wastes time going through the JWT Security Filters.
		registrationBean.setOrder(1);
		return registrationBean;
	}
}
