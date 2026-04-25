package io.github.kxng0109.quicktix.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.lettuce.core.RedisClient;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Configuration
@Profile("slice-test")
public class RateLimitTestConfig {

	@Bean
	public StringRedisTemplate stringRedisTemplate() {
		// A standard mock is perfect here since the filter only calls .hasKey()
		return Mockito.mock(StringRedisTemplate.class);
	}

	@Bean
	@SuppressWarnings("unchecked")
	public ProxyManager<byte[]> proxyManager() {
		ProxyManager<byte[]> proxyManager = Mockito.mock(ProxyManager.class, Mockito.RETURNS_DEEP_STUBS);
		Map<String, io.github.bucket4j.distributed.BucketProxy> testBucketCache = new ConcurrentHashMap<>();

		Mockito.when(proxyManager.builder().build(Mockito.any(byte[].class), Mockito.any(Supplier.class)))
		       .thenAnswer(invocation -> {
			       byte[] keyBytes = invocation.getArgument(0);
			       String key = new String(keyBytes);
			       Supplier<BucketConfiguration> configSupplier = invocation.getArgument(1);

			       return testBucketCache.computeIfAbsent(key, k -> {
				       BucketConfiguration config = configSupplier.get();

				       // 1. Create the real mathematical bucket
				       Bucket realBucket = Bucket.builder()
				                                 .addLimit(config.getBandwidths()[0])
				                                 .build();

				       // 2. Create a fake BucketProxy to satisfy the Java ClassCast check
				       io.github.bucket4j.distributed.BucketProxy fakeProxy = Mockito.mock(io.github.bucket4j.distributed.BucketProxy.class);

				       // 3. Delegate the consumption logic from the fake proxy to the real bucket
				       Mockito.when(fakeProxy.tryConsumeAndReturnRemaining(Mockito.anyLong()))
				              .thenAnswer(i -> realBucket.tryConsumeAndReturnRemaining(i.getArgument(0)));

				       return fakeProxy;
			       });
		       });

		return proxyManager;
	}

	@Bean
	public RedisClient redisClient() {
		return Mockito.mock(RedisClient.class);
	}

	@Bean
	public Supplier<BucketConfiguration> ipBucketConfiguration() {
		Bandwidth bandwidth = Bandwidth.builder()
		                               .capacity(7)
		                               .refillGreedy(60, Duration.ofHours(1))
		                               .build();

		return () -> BucketConfiguration.builder()
		                                .addLimit(bandwidth)
		                                .build();
	}

	@Bean
	public Supplier<BucketConfiguration> userBucketConfiguration() {
		Bandwidth bandwidth = Bandwidth.builder()
		                               .capacity(6)
		                               .refillGreedy(30, Duration.ofHours(1))
		                               .build();

		return () -> BucketConfiguration.builder()
		                                .addLimit(bandwidth)
		                                .build();
	}

	@Bean
	public Supplier<BucketConfiguration> holdSeatsBucketConfiguration() {
		Bandwidth bandwidth = Bandwidth.builder()
		                               .capacity(5)
		                               .refillIntervally(5, Duration.ofHours(1))
		                               .build();

		return () -> BucketConfiguration.builder()
		                                .addLimit(bandwidth)
		                                .build();
	}
}