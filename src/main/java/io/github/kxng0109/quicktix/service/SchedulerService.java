package io.github.kxng0109.quicktix.service;

import io.github.kxng0109.quicktix.entity.Event;
import io.github.kxng0109.quicktix.entity.Payment;
import io.github.kxng0109.quicktix.enums.EventStatus;
import io.github.kxng0109.quicktix.repositories.EventRepository;
import io.github.kxng0109.quicktix.repositories.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SchedulerService {

	private final SeatService seatService;
	private final BookingService bookingService;
	private final EventService eventService;
	private final PaymentRepository paymentRepository;
	private final EventRepository eventRepository;
	private final PaymentService paymentService;

	/**
	 * Periodically releases seats that have been HELD but not proceeded to booking.
	 * <p>
	 * <strong>Frequency:</strong> Every 5 minutes (300,000 ms).
	 * <br>
	 * <strong>Logic:</strong> A seat hold is considered expired if it was created more than
	 * <strong>15 minutes</strong> ago and has not been converted into a Pending Booking.
	 * This ensures inventory is returned to the pool for other customers efficiently.
	 */
	@Scheduled(fixedDelay = 300_000)
	public void releaseExpiredSeatHolds() {
		log.info("Running job: Release Expired Seat Holds");

		Instant cutoff = Instant.now()
		                        .minus(15, ChronoUnit.MINUTES);
		seatService.releaseExpiredHolds(cutoff);

		log.info("Job Completed: Release Expired Seat Holds");
	}

	/**
	 * Periodically cancels bookings that have been stuck in PENDING status.
	 * <p>
	 * <strong>Frequency:</strong> Every 5 minutes (300,000 ms).
	 * <br>
	 * <strong>Logic:</strong> A booking is considered abandoned if it has remained in
	 * {@code PENDING} status for more than <strong>15 minutes</strong>.
	 * This usually happens when a user closes the payment window without completing the transaction.
	 * The system cancels the booking and releases the associated seats.
	 */
	@Scheduled(fixedDelay = 300_000)
	public void expirePendingBookings() {
		log.info("Running job: Expire Pending Bookings");

		Instant cutoffTime = Instant.now()
		                            .minus(15, ChronoUnit.MINUTES);
		bookingService.expirePendingBookings(cutoffTime);

		log.info("Job Completed: Expire Pending Bookings");
	}

	/**
	 * Updates the status of events based on their scheduled time.
	 * <p>
	 * <strong>Frequency:</strong> Every hour (3,600,000 ms).
	 * <br>
	 * <strong>Logic:</strong> Scans for events that have passed their start time and
	 * updates their status (e.g., UPCOMING -> ONGOING or COMPLETED).
	 */
	@Scheduled(fixedDelay = 3_600_000)
	public void updateEventStatuses() {
		log.info("Running job: Update Events Statuses");

		eventService.updateEventStatus();

		log.info("Job Completed: Update Events Statuses");
	}

	/**
	 * SAFETY NET: Crash Recovery for Refunds.
	 * <p>
	 * This job acts as a backup. If the server crashes while processing an async cancellation,
	 * this scheduler will find any events that are CANCELLED but still have completed payments
	 * and retry the refunds.
	 * <p>
	 * <strong>Frequency:</strong> Every 10 minutes.
	 */
	@Scheduled(fixedDelay = 600_000)
	public void retryFailedRefunds() {
		log.info("Job Started: Safety Net - Retry Failed Refunds");

		//Find all events that are CANCELLED
		List<Event> cancelledEvents = eventRepository.findByStatus(EventStatus.CANCELLED);

		for (Event event : cancelledEvents) {
			//Check if any "COMPLETED" payments still exist for this event
			List<Payment> stuckPayments = paymentRepository.findAllCompletedPaymentsForEvent(event.getId());

			if (!stuckPayments.isEmpty()) {
				log.warn("Found {} stuck payments for Cancelled Event ID: {}. Triggering recovery.",
				         stuckPayments.size(), event.getId()
				);

				for (Payment payment : stuckPayments) {
					try {
						paymentService.processRefundForCancelledEvent(payment);
					} catch (Exception e) {
						log.error("Recovery failed for payment {}", payment.getId(), e);
					}
				}
			}
		}

		log.info("Job Completed: Safety Net");
	}

	/**
	 * Sends email reminders to users for events occurring the next day.
	 * <p>
	 * <strong>Frequency:</strong> Daily at 09:00 AM server time.
	 * <br>
	 * <strong>Logic:</strong> Finds all bookings for events scheduled within the next 24-48 hours
	 * and triggers a notification to the user.
	 */
   /* @Scheduled(cron = "0 0 9 * * *")
    public void sendEventReminders() {
        log.info("Running job: Send Events Reminders");
        //TODO: Implement notification service
        log.info("Job Completed: Send Events Reminders");
    }*/
}
