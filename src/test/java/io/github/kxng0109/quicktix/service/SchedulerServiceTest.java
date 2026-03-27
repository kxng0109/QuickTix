package io.github.kxng0109.quicktix.service;

import io.github.kxng0109.quicktix.dto.request.message.NotificationRequest;
import io.github.kxng0109.quicktix.entity.Booking;
import io.github.kxng0109.quicktix.entity.Event;
import io.github.kxng0109.quicktix.entity.Payment;
import io.github.kxng0109.quicktix.entity.User;
import io.github.kxng0109.quicktix.enums.BookingStatus;
import io.github.kxng0109.quicktix.enums.EventStatus;
import io.github.kxng0109.quicktix.enums.PaymentStatus;
import io.github.kxng0109.quicktix.repositories.BookingRepository;
import io.github.kxng0109.quicktix.repositories.EventRepository;
import io.github.kxng0109.quicktix.repositories.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

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

	@Mock
	private NotificationPublisherService notificationPublisherService;

	@Mock
	private BookingRepository bookingRepository;

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

	@Test
	public void sendEventReminders_should_publishNotifications_forConfirmedBookings() {
		Event upcomingEvent = Event.builder().id(50L).name("Tech Conference").build();

		User user = User.builder().email("attendee@example.com").build();
		Booking confirmedBooking = Booking.builder().id(100L).user(user).event(upcomingEvent)
		                                  .status(BookingStatus.CONFIRMED).build();

		when(eventRepository.findByEventStartDateTimeBetween(
				any(Instant.class),
				any(Instant.class),
				any(Pageable.class)
		))
				.thenReturn(new PageImpl<>(List.of(upcomingEvent)));

		when(bookingRepository.findByEventIdAndStatus(50L, BookingStatus.CONFIRMED))
				.thenReturn(List.of(confirmedBooking));

		schedulerService.sendEventReminders();

		verify(notificationPublisherService, times(1)).publishNotification(any(NotificationRequest.class));
		verify(eventRepository).findByEventStartDateTimeBetween(
				any(Instant.class),
				any(Instant.class),
				any(Pageable.class)
		);
		verify(bookingRepository).findByEventIdAndStatus(50L, BookingStatus.CONFIRMED);
	}

	@Test
	public void sendEventReminders_should_doNothing_when_noUpcomingEventsFound() {
		when(eventRepository.findByEventStartDateTimeBetween(
				any(Instant.class),
				any(Instant.class),
				any(Pageable.class)
		))
				.thenReturn(Page.empty());

		schedulerService.sendEventReminders();

		verify(bookingRepository, never()).findByEventIdAndStatus(anyLong(), any(BookingStatus.class));
		verify(notificationPublisherService, never()).publishNotification(any());
	}

	@Test
	public void sendEventReminders_should_doNothing_when_eventHasNoConfirmedBookings() {
		Event upcomingEvent = Event.builder().id(50L).name("Empty Concert").build();

		when(eventRepository.findByEventStartDateTimeBetween(
				any(Instant.class),
				any(Instant.class),
				any(Pageable.class)
		))
				.thenReturn(new PageImpl<>(List.of(upcomingEvent)));

		when(bookingRepository.findByEventIdAndStatus(50L, BookingStatus.CONFIRMED))
				.thenReturn(Collections.emptyList());

		schedulerService.sendEventReminders();

		verify(eventRepository).findByEventStartDateTimeBetween(
				any(Instant.class),
				any(Instant.class),
				any(Pageable.class)
		);
		verify(bookingRepository).findByEventIdAndStatus(50L, BookingStatus.CONFIRMED);
		verify(notificationPublisherService, never()).publishNotification(any());
	}
}
