package io.github.kxng0109.quicktix.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Configuration class for rate limiting using Redis and Bucket4j.
 * <p>
 * This class sets up the necessary infrastructure to apply and enforce rate limits
 * on APIs or other parts of the application using the {@link io.github.bucket4j.Bucket Bucket4j} library with
 * Redis as the backing store. It ensures that API consumers are restricted to a defined
 * number of operations within a specific time window, which helps prevent abuse and keeps
 * the application responsive under heavy loads.
 * </p>
 * <p>
 * The rate limiting functionality is backed by a Redis instance defined using parameters
 * such as host, port, and password that are configurable from the application's properties.
 * </p>
 */
@Configuration
public class RateLimitConfig {

	@Value("${spring.data.redis.host}")
	private String redisHost;

	@Value("${spring.data.redis.port}")
	private int redisPort;

	@Value("${spring.data.redis.password:}")
	private String redisPassword;

	/**
	 * Creates and configures a {@link RedisClient} instance for interacting with the Redis server.
	 * <p>
	 * This method builds a {@link RedisURI} using the host, port, and optionally, the password
	 * provided via the class fields {@code redisHost}, {@code redisPort}, and {@code redisPassword}.
	 * It ensures secure connectivity to the Redis instance, with support for authenticated access
	 * when a non-empty password is specified. The returned {@link RedisClient} serves as the foundation
	 * for executing Redis commands and managing Redis-based services like distributed rate limiting
	 * or caching in the application.
	 * </p>
	 *
	 * <p>
	 * <strong>Usage example:</strong> This method is typically used as a Spring Bean to provide
	 * a singleton {@link RedisClient} instance to other components requiring Redis connectivity.
	 * </p>
	 *
	 * @return a fully initialized {@link RedisClient} instance configured to interact with the Redis server.
	 * Returns a non-null client unless an unchecked exception occurs during initialization.
	 * @throws IllegalStateException if the Redis configuration parameters are invalid (e.g., a null or empty host).
	 * @implNote Ensure the provided {@code redisHost} and {@code redisPort} point to a reachable Redis instance.
	 * For production environments, use secure configurations such as a strong password or TLS.
	 */
	@Bean
	public RedisClient redisClient() {
		RedisURI.Builder redisURI = RedisURI.builder()
		                                    .withHost(redisHost)
		                                    .withPort(redisPort);

		if (redisPassword != null && !redisPassword.isEmpty()) {
			redisURI.withPassword(redisPassword.toCharArray());
		}

		return RedisClient.create(redisURI.build());
	}

	/**
	 * Creates and configures a {@link ProxyManager} instance for managing rate-limited buckets using Redis.
	 * <p>
	 * This method integrates with {@link Bucket4jLettuce} to utilize the Redis-based implementation of
	 * the {@link ProxyManager}. It employs a "Compare-And-Swap" strategy for bucket updates and applies an
	 * expiration policy based on time to refill the bucket to its maximum capacity. The expiration is set
	 * to a default interval of 10 seconds. This setup is crucial for supporting distributed rate limiting
	 * with consistent bucket state across multiple application instances.
	 * </p>
	 * <ul>
	 *   <li><strong>Thread safety:</strong> All operations on the {@link ProxyManager} are thread-safe as they
	 *       leverage Redis as a locking mechanism.</li>
	 *   <li><strong>Performance:</strong> Proper caching and expiration strategies ensure high throughput and
	 *       minimal Redis overhead.</li>
	 * </ul>
	 *
	 * @param redisClient the client used to connect to the Redis instance; cannot be {@code null}.
	 *                    This client must be properly initialized and connected to a running Redis server.
	 * @return a fully configured {@link ProxyManager<byte[]> ProxyManager} instance that can be used to create and manage
	 * rate-limited {@code Bucket}s.
	 * @throws IllegalArgumentException if the {@code redisClient} is {@code null}.
	 * @implNote Ensure the {@code RedisClient} points to a highly available Redis instance for
	 * reliable operation in distributed environments.
	 * @see Bucket4jLettuce#casBasedBuilder(RedisClient)
	 * @see ExpirationAfterWriteStrategy#basedOnTimeForRefillingBucketUpToMax(Duration)
	 */
	@Bean
	public ProxyManager<byte[]> proxyManager(RedisClient redisClient) {
		return Bucket4jLettuce.casBasedBuilder(redisClient)
		                      .expirationAfterWrite(
				                      ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(
						                      Duration.ofSeconds(10)
				                      )
		                      ).build();
	}

	/**
	 * Provides the configuration for creating {@link BucketConfiguration} instances with predefined
	 * rate-limiting parameters.
	 * <p>
	 * This method defines a rate-limiting policy using a {@link Bandwidth} configuration, which specifies
	 * a total capacity of 20 tokens and a refill interval of 10 tokens every 10 seconds. The configuration
	 * is encapsulated within a {@link BucketConfiguration} object and returned as a {@link Supplier},
	 * allowing it to be lazily instantiated and reused wherever required.
	 * </p>
	 * <p>
	 * This setup is critical for enforcing rate limits in the application, ensuring that consumers of
	 * APIs are limited to a controlled number of requests within a given interval to maintain system
	 * stability and prevent abuse.
	 * </p>
	 *
	 * @return a {@link Supplier} of {@link BucketConfiguration}, pre-configured with a single
	 * rate-limiting {@link Bandwidth} instance defining a capacity of 20 tokens and a refill interval
	 * of 10 tokens every 10 seconds.
	 */
	@Bean
	public Supplier<BucketConfiguration> bucketConfiguration() {
		Bandwidth bandwidth = Bandwidth.builder()
		                               .capacity(2)
		                               .refillIntervally(10, Duration.ofSeconds(10))
		                               .build();

		return () -> BucketConfiguration.builder()
		                                .addLimit(bandwidth)
		                                .build();
	}
}
