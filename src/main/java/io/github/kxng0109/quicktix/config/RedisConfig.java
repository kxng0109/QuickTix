package io.github.kxng0109.quicktix.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * Global configuration for Spring Cache.
 * <p>
 * Enables application-wide caching mechanisms. This configuration is intentionally
 * disabled when the "test" profile is active (via {@code @Profile("!test")}) to prevent
 * testing environments from attempting to connect to a live Redis instance.
 * </p>
 */
@Configuration
@EnableCaching
@Profile("!slice-test")
public class RedisConfig {

	@Bean
	public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
		GenericJacksonJsonRedisSerializer serializer = GenericJacksonJsonRedisSerializer.builder()
		                                                                                .enableUnsafeDefaultTyping()
		                                                                                .enableSpringCacheNullValueSupport()
		                                                                                .build();

		RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
		                                                        .entryTtl(Duration.ofMinutes(60))
		                                                        .disableCachingNullValues()
		                                                        .serializeValuesWith(
				                                                        RedisSerializationContext.SerializationPair.fromSerializer(serializer)
		                                                        );

		return RedisCacheManager.builder(connectionFactory)
		                        .cacheDefaults(config)
		                        .build();
	}
}
