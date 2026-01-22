package io.github.kxng0109.quicktix.listener;

import io.github.kxng0109.quicktix.entity.Payment;
import io.github.kxng0109.quicktix.event.EventCancelledEvent;
import io.github.kxng0109.quicktix.repositories.PaymentRepository;
import io.github.kxng0109.quicktix.service.BookingService;
import io.github.kxng0109.quicktix.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventCancellationListener {

	private final PaymentRepository paymentRepository;
	private final PaymentService paymentService;
	private final BookingService bookingService;

	@Async
	@EventListener
	public void handleEventCancellation(EventCancelledEvent event) {
		Long eventId = event.eventId();
		log.info("Starting background refund process for Event ID: {}", eventId);

		List<Payment> payments = paymentRepository.findAllCompletedPaymentsForEvent(eventId);
		log.info("Found {} payments for Event ID '{}' to refund", payments.size(), eventId);

		for (Payment payment : payments) {
			try {
				paymentService.processRefundForCancelledEvent(payment);
			} catch (Exception e) {
				log.error("Failed to process refund for payment ID: {}", payment.getId(), e);
			}
		}

		bookingService.expirePendingBookings(eventId);
		log.info("Completed background refund process for Event ID: {}", eventId);
	}
}
