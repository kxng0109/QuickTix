package io.github.kxng0109.quicktix.config;

import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Global configuration for RabbitMQ messaging infrastructure.
 * <p>
 * Defines the serialization strategy for all messages sent to and received from the RabbitMQ broker.
 * By injecting the {@link JacksonJsonMessageConverter}, this configuration ensures that all Java objects
 * (like {@code NotificationRequest}) are automatically serialized into standardized JSON payloads
 * before transmission, rather than relying on native Java serialization.
 * </p>
 */
@Configuration
public class RabbitMQConfig {

	@Bean
	public MessageConverter jsonMessageConverter() {
		return new JacksonJsonMessageConverter();
	}
}
