package io.github.kxng0109.quicktix.service;

import io.github.kxng0109.quicktix.dto.request.message.NotificationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationPublisherService {

	private final RabbitTemplate rabbitTemplate;

	@Value("${app.rabbitmq.exchange}")
	private String exchange;

	@Value("${app.rabbitmq.routing-key}")
	private String routingKey;

	/**
	 * Serializes the payload to JSON, attaches required NotifyHub headers,
	 * and dispatches it to the delayed exchange.
	 */
	public void publishNotification(NotificationRequest payload) {
		try {
			// NotifyHub's consumer relies on this header for exponential backoff routing
			MessagePostProcessor postProcessor = message -> {
				message.getMessageProperties().getHeaders().put("x-retry-count", 0);
				return message;
			};

			rabbitTemplate.convertAndSend(exchange, routingKey, payload, postProcessor);
			log.info("Successfully published notification task to RabbitMQ for recipients: {}", payload.to());
		} catch (Exception e) {
			log.error("Failed to publish notification task to RabbitMQ.", e);
		}
	}
}
