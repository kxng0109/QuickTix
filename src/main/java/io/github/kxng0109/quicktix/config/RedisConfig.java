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
import java.util.HashMap;
import java.util.Map;

/**
 * Global configuration for Spring Cache using Redis.
 * <p>
 * Configures default serialization and TTLs, while providing specific short-lived
 * micro-caches for highly volatile data like available seat inventory.
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

		RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
		                                                               .entryTtl(Duration.ofMinutes(60))
		                                                               .disableCachingNullValues()
		                                                               .serializeValuesWith(
				                                                               RedisSerializationContext.SerializationPair
						                                                               .fromSerializer(serializer)
		                                                               );

		Map<String, RedisCacheConfiguration> specificCacheConfigs = new HashMap<>();
		specificCacheConfigs.put("availableSeats", defaultConfig.entryTtl(Duration.ofSeconds(5)));

		return RedisCacheManager.builder(connectionFactory)
		                        .cacheDefaults(defaultConfig)
		                        .withInitialCacheConfigurations(specificCacheConfigs)
		                        .build();
	}
}
