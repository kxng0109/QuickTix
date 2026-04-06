package io.github.kxng0109.quicktix.listener;

import io.github.kxng0109.quicktix.entity.Payment;
import io.github.kxng0109.quicktix.enums.PaymentStatus;
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

/**
 * Asynchronous event listener for handling the aftermath of an event cancellation.
 * <p>
 * Decouples the immediate HTTP response of the Admin cancellation request from the
 * heavy, time-consuming process of refunding transactions. It operates in a separate thread,
 * fetching all completed payments for the cancelled event and delegating them to the
 * {@link PaymentService} for individual processing.
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventCancellationListener {

	private final PaymentRepository paymentRepository;
	private final PaymentService paymentService;
	private final BookingService bookingService;

	/**
	 * Intercepts the {@link EventCancelledEvent} and begins the mass-refund protocol.
	 * <p>
	 * Iterates through all successful payments associated with the event and attempts
	 * a gateway refund. It also triggers the expiration of any lingering pending bookings
	 * to free up database and Redis resources.
	 * </p>
	 *
	 * @param event The record containing the ID of the cancelled event.
	 */
	@Async
	@EventListener
	public void handleEventCancellation(EventCancelledEvent event) {
		Long eventId = event.eventId();
		log.info("Starting background refund process for Event ID: {}", eventId);

		//Using it to get all payments for an event in order to issue a refund for canceled events
		List<Payment> payments = paymentRepository.findByBooking_EventIdAndStatus(
				eventId,
				PaymentStatus.COMPLETED
		);
		log.info("Found {} payments for Event ID '{}' to refund", payments.size(), eventId);

		for (Payment payment : payments) {
			try {
				paymentService.processRefundForCancelledEvent(payment.getId());
			} catch (Exception e) {
				log.error("Failed to process refund for payment ID: {}", payment.getId(), e);
			}
		}

		bookingService.expirePendingBookings(eventId);
		log.info("Completed background refund process for Event ID: {}", eventId);
	}
}
