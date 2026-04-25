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
import org.springframework.context.annotation.Profile;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Centralized configuration for distributed rate limiting within the QuickTix ticket booking system.
 * <p>
 * This configuration class establishes the infrastructure required to throttle incoming requests,
 * protect backend resources, and ensure fair access during high-traffic events. It leverages
 * <strong>Bucket4j</strong> for the token-bucket algorithm and utilizes <strong>Redis (via Lettuce)</strong>
 * as a distributed state backend. This guarantees that rate limit consumption remains perfectly
 * synchronized across multiple application instances in a scalable environment.
 * </p>
 *
 * <h3>Architectural Components</h3>
 * <ul>
 * <li>{@link RedisClient}: Manages the secure, resilient connection to the Redis datastore.</li>
 * <li>{@link ProxyManager}: Serves as the distributed engine for Bucket4j, executing
 * Compare-And-Swap (CAS) operations to ensure atomic, thread-safe bucket updates without
 * heavy locking overhead.</li>
 * </ul>
 *
 * <h3>Rate Limiting Profiles</h3>
 * This class exposes several {@link Supplier} beans that define the bandwidth constraints for
 * different layers of the application:
 * <ul>
 * <li><strong>IP Traffic ({@link #ipBucketConfiguration()}):</strong> Provides global traffic shaping
 * to mitigate DDoS attacks or aggressive scraping.</li>
 * <li><strong>User Operations ({@link #userBucketConfiguration()}):</strong> Controls the general
 * velocity of authenticated user actions to prevent API abuse.</li>
 * <li><strong>Seat Holds ({@link #holdSeatsBucketConfiguration()}):</strong> A strict, highly
 * constrained profile designed specifically to manage concurrency and ensure fairness when
 * users are attempting to reserve tickets.</li>
 * </ul>
 *
 * @see io.github.bucket4j.Bucket
 * @see io.github.bucket4j.distributed.proxy.ProxyManager
 * @see io.lettuce.core.RedisClient
 */
@Configuration
@Profile("!slice-test")
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
	 * @return a fully configured {@link ProxyManager ProxyManager} instance that can be used to create and manage
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
	 * Creates a {@link Supplier} that provides a pre-configured {@link BucketConfiguration} for rate-limiting IP traffic.
	 *
	 * <p>This method defines a {@link BucketConfiguration} with a single {@link Bandwidth} limit, allowing up to 150 tokens
	 * as the maximum capacity. The bucket refills at a rate of 60 tokens per minute using a greedy refill strategy.
	 * The configuration is intended for controlling incoming IP-based traffic to ensure fair usage or prevent abuse.
	 *
	 * <p>This can be used in scenarios such as API rate limiting, where each IP address is allocated a maximum number
	 * of requests within a certain time window.
	 *
	 * @return a {@link Supplier} instance that provides the configured {@link BucketConfiguration} with the specified
	 * bandwidth constraints.
	 * @implNote The {@link Bandwidth} limit uses a "greedy" refill strategy, which immediately adds available tokens upon
	 * refill rather than distributing them gradually.
	 * @see BucketConfiguration
	 * @see Bandwidth
	 * @see Supplier
	 */
	@Bean
	@Profile("!test")
	public Supplier<BucketConfiguration> ipBucketConfiguration() {
		Bandwidth bandwidth = Bandwidth.builder()
		                               .capacity(150)
		                               .refillGreedy(60, Duration.ofMinutes(1))
		                               .build();

		return () -> BucketConfiguration.builder()
		                                .addLimit(bandwidth)
		                                .build();
	}

	/**
	 * Provides a supplier for the bucket configuration used to manage rate-limiting of user operations.
	 * <p>
	 * This method defines a {@link BucketConfiguration} with specific bandwidth limits tailored to control
	 * the frequency of user-related API requests or actions. The bucket is configured with a capacity of
	 * 60 operations and allows refilling of 30 tokens every minute. This ensures a balance between user
	 * experience and system resource protection by limiting excessive usage. The {@link Supplier} pattern
	 * is utilized to make the configuration dynamically available to components that require it.
	 * </p>
	 *
	 * <ul>
	 *   <li><strong>Bandwidth configuration:</strong> The bucket's capacity is set to 60, with a greedy refill
	 *       strategy that adds 30 tokens every minute.</li>
	 *   <li><strong>Dynamic usage:</strong> Encapsulating the configuration within a {@link Supplier} simplifies
	 *       its injection into other components where rate-limiting constraints are necessary.</li>
	 * </ul>
	 *
	 * @return a {@link Supplier} providing a {@link BucketConfiguration} configured with bandwidth limits specifically
	 * designed for user operation rate-limiting. The supplier is guaranteed to be non-null.
	 * @implNote This method supports centralized rate-limiting configurations for distributing resource access fairly
	 * across users while protecting the system against abuse. It is suited for scalable architectures and distributed
	 * environments.
	 * @see BucketConfiguration
	 * @see Bandwidth
	 */
	@Bean
	@Profile("!test")
	public Supplier<BucketConfiguration> userBucketConfiguration() {
		Bandwidth bandwidth = Bandwidth.builder()
		                               .capacity(60)
		                               .refillGreedy(30, Duration.ofMinutes(1))
		                               .build();

		return () -> BucketConfiguration.builder()
		                                .addLimit(bandwidth)
		                                .build();
	}

	/**
	 * Provides a supplier for the bucket configuration used to manage rate-limiting of seat hold operations.
	 * <p>
	 * This method defines a {@link BucketConfiguration} with specific bandwidth limits tailored to control
	 * the frequency of holding seats. The bucket is configured to allow up to 5 seat hold operations per minute,
	 * ensuring fair usage and preventing resource abuse. The {@link Supplier} encapsulates the configuration to
	 * enable dynamic injection into components requiring rate-limiting behavior.
	 * </p>
	 *
	 * <ul>
	 *   <li><strong>Bandwidth configuration:</strong> Allows a capacity of 5 operations to be refilled incrementally
	 *       every minute.</li>
	 *   <li><strong>Dynamic usage:</strong> The {@link Supplier} makes it easy to supply the configuration in
	 *       rate-limiting scenarios where hold seat operations are constrained.</li>
	 * </ul>
	 *
	 * @return a {@link Supplier} providing a {@link BucketConfiguration} with bandwidth limits designed
	 * for seat hold rate-limiting. The supplier is never {@code null}.
	 * @implNote This method is intended to be used in environments where rate-limited seat holds are necessary
	 * to manage fair distribution of resources among concurrent users.
	 * @see RateLimitConfig#proxyManager
	 */
	@Bean
	@Profile("!test")
	public Supplier<BucketConfiguration> holdSeatsBucketConfiguration() {
		Bandwidth bandwidth = Bandwidth.builder()
		                               .capacity(5)
		                               .refillIntervally(5, Duration.ofMinutes(1))
		                               .build();

		return () -> BucketConfiguration.builder()
		                                .addLimit(bandwidth)
		                                .build();
	}

	@Bean("ipBucketConfiguration")
	@Profile("test")
	public Supplier<BucketConfiguration> ipBucketConfigurationTest() {
		return () -> BucketConfiguration.builder()
		                                .addLimit(Bandwidth.builder()
		                                                   .capacity(15)
		                                                   .refillGreedy(15, Duration.ofHours(1))
		                                                   .build())
		                                .build();
	}

	@Bean("userBucketConfiguration")
	@Profile("test")
	public Supplier<BucketConfiguration> userBucketConfigurationTest() {
		return () -> BucketConfiguration.builder()
		                                .addLimit(
				                                Bandwidth.builder()
				                                         .capacity(14)
				                                         .refillGreedy(14, Duration.ofHours(1))
				                                         .build())
		                                .build();
	}

	@Bean("holdSeatsBucketConfiguration")
	@Profile("test")
	public Supplier<BucketConfiguration> holdSeatsBucketConfigurationTest() {
		return () -> BucketConfiguration.builder()
		                                .addLimit(Bandwidth.builder()
		                                                   .capacity(5)
		                                                   .refillIntervally(1, Duration.ofMinutes(10))
		                                                   .build())
		                                .build();
	}
}
