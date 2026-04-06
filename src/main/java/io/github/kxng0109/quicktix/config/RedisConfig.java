package io.github.kxng0109.quicktix.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

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
@Profile("!test")
public class RedisConfig {
}
