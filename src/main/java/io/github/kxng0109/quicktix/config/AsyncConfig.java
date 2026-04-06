package io.github.kxng0109.quicktix.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables Spring's asynchronous method execution capabilities.
 * <p>
 * By providing the {@code @EnableAsync} annotation here, methods across the application
 * annotated with {@code @Async} will be executed in a separate thread pool. This is critical
 * for non-blocking background tasks, such as dispatching RabbitMQ messages or sending
 * notification emails without holding up the client's HTTP request thread.
 * </p>
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
