package io.github.kxng0109.quicktix.service;

import io.github.kxng0109.quicktix.entity.Event;
import io.github.kxng0109.quicktix.entity.Payment;
import io.github.kxng0109.quicktix.enums.EventStatus;
import io.github.kxng0109.quicktix.enums.PaymentStatus;
import io.github.kxng0109.quicktix.repositories.EventRepository;
import io.github.kxng0109.quicktix.repositories.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SchedulerServiceTest {

	@Mock
	private SeatService seatService;

	@Mock
	private BookingService bookingService;

	@Mock
	private EventService eventService;

	@Mock
	private PaymentRepository paymentRepository;

	@Mock
	private EventRepository eventRepository;

	@Mock
	private PaymentService paymentService;

	@InjectMocks
	private SchedulerService schedulerService;

	@Test
	public void releaseExpiredSeatHolds_should_callSeatService_withCutoffTime() {
		schedulerService.releaseExpiredSeatHolds();

		verify(seatService).releaseExpiredHolds(any(Instant.class));
	}

	@Test
	public void expirePendingBookings_should_callBookingService_withCutoffTime() {
		schedulerService.expirePendingBookings();

		verify(bookingService).expirePendingBookings(any(Instant.class));
	}

	@Test
	public void updateEventStatuses_should_callEventService() {
		schedulerService.updateEventStatuses();

		verify(eventService).updateEventStatus();
	}

	@Test
	public void retryFailedRefunds_should_triggerRefund_when_stuckPaymentsFound() {
		Long eventId = 10L;
		Event cancelledEvent = Event.builder()
		                            .id(eventId)
		                            .status(EventStatus.CANCELLED)
		                            .build();

		Payment stuckPayment = Payment.builder()
		                              .id(100L)
		                              .status(PaymentStatus.COMPLETED) // It should have been REFUNDED, so it's "stuck"
		                              .build();

		when(eventRepository.findByStatus(EventStatus.CANCELLED))
				.thenReturn(List.of(cancelledEvent));

		when(paymentRepository.findAllCompletedPaymentsForEvent(eventId))
				.thenReturn(List.of(stuckPayment));

		schedulerService.retryFailedRefunds();

		verify(paymentService).processRefundForCancelledEvent(stuckPayment);
	}

	@Test
	public void retryFailedRefunds_should_doNothing_when_noStuckPaymentsFound() {
		Event cancelledEvent = Event.builder().id(10L).status(EventStatus.CANCELLED).build();

		when(eventRepository.findByStatus(EventStatus.CANCELLED))
				.thenReturn(List.of(cancelledEvent));

		when(paymentRepository.findAllCompletedPaymentsForEvent(10L))
				.thenReturn(Collections.emptyList());

		schedulerService.retryFailedRefunds();

		verify(paymentService, never()).processRefundForCancelledEvent(any());
	}

	@Test
	public void retryFailedRefunds_should_continueProcessing_evenIfOneRefundFails() {
		Event event = Event.builder().id(1L).status(EventStatus.CANCELLED).build();
		Payment payment1 = Payment.builder().id(101L).build();
		Payment payment2 = Payment.builder().id(102L).build();

		when(eventRepository.findByStatus(EventStatus.CANCELLED)).thenReturn(List.of(event));
		when(paymentRepository.findAllCompletedPaymentsForEvent(1L))
				.thenReturn(List.of(payment1, payment2));

		doThrow(new RuntimeException("Gateway Error"))
				.when(paymentService).processRefundForCancelledEvent(payment1);

		schedulerService.retryFailedRefunds();

		verify(paymentService).processRefundForCancelledEvent(payment1);
		verify(paymentService).processRefundForCancelledEvent(payment2);
	}
}
