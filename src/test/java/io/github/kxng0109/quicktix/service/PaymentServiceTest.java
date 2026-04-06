package io.github.kxng0109.quicktix.service;

import io.github.kxng0109.quicktix.dto.request.PaymentRequest;
import io.github.kxng0109.quicktix.dto.response.PaymentResponse;
import io.github.kxng0109.quicktix.entity.Booking;
import io.github.kxng0109.quicktix.entity.Event;
import io.github.kxng0109.quicktix.entity.Payment;
import io.github.kxng0109.quicktix.entity.User;
import io.github.kxng0109.quicktix.enums.BookingStatus;
import io.github.kxng0109.quicktix.enums.PaymentMethod;
import io.github.kxng0109.quicktix.enums.PaymentStatus;
import io.github.kxng0109.quicktix.enums.Role;
import io.github.kxng0109.quicktix.exception.InvalidOperationException;
import io.github.kxng0109.quicktix.exception.PaymentFailedException;
import io.github.kxng0109.quicktix.repositories.BookingRepository;
import io.github.kxng0109.quicktix.repositories.PaymentRepository;
import io.github.kxng0109.quicktix.service.gateway.PaymentGateway;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PaymentServiceTest {

	private final Long paymentId = 100L;
	private final Long bookingId = 200L;
	private final BigDecimal totalAmount = BigDecimal.valueOf(12345.68);
	private final BookingStatus bookingStatus = BookingStatus.PENDING;
	private final PaymentStatus paymentStatus = PaymentStatus.PENDING;
	private final String transferReference = UUID.randomUUID().toString();
	private final PaymentMethod paymentMethod = PaymentMethod.BANK_TRANSFER;
	private final PaymentRequest request = PaymentRequest.builder()
	                                                     .bookingId(bookingId)
	                                                     .paymentMethod(paymentMethod)
	                                                     .build();

	@Mock
	private PaymentRepository paymentRepository;

	@Mock
	private BookingRepository bookingRepository;

	@Mock
	private BookingService bookingService;

	@Mock
	private PaymentGateway paymentGateway;

	@Mock
	private NotificationPublisherService notificationPublisherService;

	@InjectMocks
	private PaymentService paymentService;

	private Payment payment;
	private Booking booking;
	private User user;
	private Event event;

	@BeforeEach
	void setUp() {
		user = User.builder()
		           .id(300L)
		           .role(Role.USER)
		           .email("test@example.com")
		           .build();

		event = Event.builder()
		             .id(1000L)
		             .name("An event")
		             .build();

		booking = Booking.builder()
		                 .id(bookingId)
		                 .status(bookingStatus)
		                 .bookingReference(UUID.randomUUID().toString())
		                 .totalAmount(totalAmount)
		                 .user(user)
		                 .event(event)
		                 .build();

		payment = Payment.builder()
		                 .id(paymentId)
		                 .booking(booking)
		                 .amount(totalAmount)
		                 .status(paymentStatus)
		                 .paymentMethod(paymentMethod)
		                 .transactionReference(transferReference)
		                 .paidAt(Instant.now())
		                 .build();

		booking.setPayment(payment);
		lenient().when(paymentGateway.refundTransaction(transferReference)).thenReturn(true);
	}

	@Test
	public void getPaymentByBookingId_should_returnPaymentResponse_when_paymentIsFound() {
		when(paymentRepository.findByBookingId(anyLong()))
				.thenReturn(Optional.of(payment));

		PaymentResponse response = paymentService.getPaymentByBookingId(paymentId, user);

		assertNotNull(response);
		assertEquals(paymentStatus.getDisplayName(), response.status());
		assertEquals(paymentId, response.paymentId());

		verify(paymentRepository).findByBookingId(anyLong());
	}

	@Test
	public void getPaymentByBookingId_should_throwEntityNotFoundException_when_paymentIsNotFound() {
		when(paymentRepository.findByBookingId(anyLong()))
				.thenReturn(Optional.empty());

		assertThrows(
				EntityNotFoundException.class,
				() -> paymentService.getPaymentByBookingId(paymentId, user)
		);

		verify(paymentRepository).findByBookingId(anyLong());
	}

	@Test
	public void getPaymentByBookingId_should_throwAccessDeniedException_when_userDoesNotOwnPaymentAndNotAdmin() {
		User anotherUser = User.builder()
		                       .id(999L)
		                       .role(Role.USER)
		                       .build();

		when(paymentRepository.findByBookingId(anyLong()))
				.thenReturn(Optional.of(payment));

		assertThrows(
				AccessDeniedException.class,
				() -> paymentService.getPaymentByBookingId(paymentId, anotherUser)
		);

		verify(paymentRepository).findByBookingId(anyLong());
	}

	@Test
	public void initializePayment_should_returnAPaymentResponse_when_requestIsValid() {
		when(bookingRepository.findById(anyLong()))
				.thenReturn(Optional.of(booking));
		when(paymentRepository.save(any(Payment.class)))
				.thenReturn(payment);
		when(paymentGateway.initializePayment(any(Payment.class)))
				.thenReturn("clientSecret");

		PaymentResponse response = paymentService.initializePayment(request, user);

		assertNotNull(response);
		assertEquals(paymentStatus.getDisplayName(), response.status());
		assertEquals(paymentMethod.getDisplayName(), response.paymentMethod());
		assertEquals(paymentId, response.paymentId());
		assertEquals("clientSecret", response.clientSecret());

		verify(bookingRepository).findById(anyLong());
		verify(paymentRepository).save(any(Payment.class));
	}

	@Test
	public void initializePayment_should_throwEntityNotFoundException_when_userDoesNotOwnBooking() {
		// Create a malicious user with a different ID than the one on the booking
		User maliciousUser = User.builder()
		                         .id(999L)
		                         .role(Role.USER)
		                         .email("hacker@example.com")
		                         .build();

		when(bookingRepository.findById(anyLong()))
				.thenReturn(Optional.of(booking)); // The booking belongs to 'user' (ID 300L)

		EntityNotFoundException ex = assertThrows(
				EntityNotFoundException.class,
				() -> paymentService.initializePayment(request, maliciousUser)
		);

		assertEquals("Booking not found", ex.getMessage());

		verify(bookingRepository).findById(anyLong());
		verify(paymentRepository, never()).save(any(Payment.class)); // Ensure no payment is created
	}

	@Test
	public void initializePayment_should_throwEntityNotFoundException_when_bookingIsNotFound() {
		when(bookingRepository.findById(anyLong()))
				.thenReturn(Optional.empty());

		assertThrows(
				EntityNotFoundException.class,
				() -> paymentService.initializePayment(request, user)
		);

		verify(bookingRepository).findById(anyLong());
		verify(paymentRepository, never()).save(any(Payment.class));
	}

	@Test
	public void initializePayment_should_throwInvalidOperationException_when_bookingStatusIsNotPending() {
		booking.setStatus(BookingStatus.CONFIRMED);

		when(bookingRepository.findById(anyLong()))
				.thenReturn(Optional.of(booking));

		assertThrows(
				InvalidOperationException.class,
				() -> paymentService.initializePayment(request, user)
		);

		verify(bookingRepository).findById(anyLong());
		verify(paymentRepository, never()).save(any(Payment.class));
	}


	@Test
	public void handleSuccessfulWebhookPayment_should_updateStatusAndConfirmBooking() {
		payment.setStatus(PaymentStatus.PENDING);

		when(paymentRepository.findByBookingId(any())).thenReturn(Optional.of(payment));

		paymentService.handleSuccessfulWebhookPayment(1L, "pi_12345");

		assertEquals(PaymentStatus.COMPLETED, payment.getStatus());
		assertEquals("pi_12345", payment.getTransactionReference());
		assertNotNull(payment.getPaidAt());

		verify(paymentRepository).save(payment);
		verify(paymentRepository).findByBookingId(any());
		verify(bookingService).confirmBooking(payment.getBooking().getId());
	}

	@Test
	public void handleSuccessfulWebhookPayment_should_returnEarly_when_alreadyCompleted() {
		payment.setStatus(PaymentStatus.COMPLETED);

		when(paymentRepository.findByBookingId(any()))
				.thenReturn(Optional.of(payment));

		paymentService.handleSuccessfulWebhookPayment(1L, "pi_12345");

		verify(paymentRepository).findByBookingId(any());
		verify(paymentRepository, never()).save(any(Payment.class));
		verify(bookingService, never()).confirmBooking(anyLong());
	}

	@Test
	public void handleSuccessfulWebhookPayment_should_throwEntityNotFoundException_when_paymentDoesNotExist() {
		assertThrows(
				EntityNotFoundException.class,
				() -> paymentService.handleSuccessfulWebhookPayment(999L, "pi_12345")
		);

		verify(bookingService, never()).confirmBooking(anyLong());
	}

	@Test
	public void refundPayment_should_refundPaymentAndReturnNothing_when_paymentStatusIsCompleted() {
		payment.setStatus(PaymentStatus.COMPLETED);

		when(paymentRepository.findByBookingId(anyLong()))
				.thenReturn(Optional.of(payment));

		paymentService.refundPayment(bookingId);

		assertEquals(PaymentStatus.REFUNDED, payment.getStatus());

		verify(paymentRepository).findByBookingId(anyLong());
		verify(paymentGateway).refundTransaction(anyString());
		verify(paymentRepository).save(any(Payment.class));
		verify(bookingService).cancelRefundedBooking(anyLong());
	}

	@Test
	public void refundPayment_should_throwEntityNotFoundException_when_bookingIsNotFound() {
		when(paymentRepository.findByBookingId(anyLong()))
				.thenReturn(Optional.empty());

		assertThrows(
				EntityNotFoundException.class,
				() -> paymentService.refundPayment(bookingId)
		);

		verify(paymentRepository).findByBookingId(anyLong());
		verify(paymentGateway, never()).refundTransaction(anyString());
		verify(paymentRepository, never()).save(any(Payment.class));
		verify(bookingService, never()).cancelRefundedBooking(anyLong());
	}

	@Test
	public void refundPayment_should_throwInvalidOperationException_when_paymentStatusIsRefunded() {
		payment.setStatus(PaymentStatus.REFUNDED);

		when(paymentRepository.findByBookingId(anyLong()))
				.thenReturn(Optional.of(payment));

		InvalidOperationException ex = assertThrows(
				InvalidOperationException.class,
				() -> paymentService.refundPayment(bookingId)
		);

		assertEquals("Payment has already been refunded", ex.getMessage());

		verify(paymentRepository).findByBookingId(anyLong());
		verify(paymentGateway, never()).refundTransaction(anyString());
		verify(paymentRepository, never()).save(any(Payment.class));
		verify(bookingService, never()).cancelRefundedBooking(anyLong());
	}

	@Test
	public void refundPayment_should_throwInvalidOperationException_when_paymentStatusIsPending() {
		when(paymentRepository.findByBookingId(anyLong()))
				.thenReturn(Optional.of(payment));

		InvalidOperationException ex = assertThrows(
				InvalidOperationException.class,
				() -> paymentService.refundPayment(bookingId)
		);

		assertEquals("Cannot refund payment if payment was not completed.", ex.getMessage());

		verify(paymentRepository).findByBookingId(anyLong());
		verify(paymentGateway, never()).refundTransaction(anyString());
		verify(paymentRepository, never()).save(any(Payment.class));
		verify(bookingService, never()).cancelRefundedBooking(anyLong());
	}

	@Test
	public void refundPayment_should_throwInvalidOperationException_when_paymentStatusIsFailed() {
		payment.setStatus(PaymentStatus.FAILED);

		when(paymentRepository.findByBookingId(anyLong()))
				.thenReturn(Optional.of(payment));

		InvalidOperationException ex = assertThrows(
				InvalidOperationException.class,
				() -> paymentService.refundPayment(bookingId)
		);

		assertEquals("Cannot refund payment if payment was not completed.", ex.getMessage());

		verify(paymentRepository).findByBookingId(anyLong());
		verify(paymentGateway, never()).refundTransaction(anyString());
		verify(paymentRepository, never()).save(any(Payment.class));
		verify(bookingService, never()).cancelRefundedBooking(anyLong());
	}

	@Test
	public void refundPayment_should_throwPaymentFailedException_when_refundFailed() {
		payment.setStatus(PaymentStatus.COMPLETED);

		when(paymentRepository.findByBookingId(anyLong()))
				.thenReturn(Optional.of(payment));
		when(paymentGateway.refundTransaction(anyString()))
				.thenReturn(false);

		PaymentFailedException ex = assertThrows(
				PaymentFailedException.class,
				() -> paymentService.refundPayment(bookingId)
		);

		assertEquals("Refund failed at gateway.", ex.getMessage());

		verify(paymentRepository).findByBookingId(anyLong());
		verify(paymentGateway).refundTransaction(anyString());
		verify(paymentRepository, never()).save(any(Payment.class));
		verify(bookingService, never()).cancelRefundedBooking(anyLong());
	}

	@Test
	public void processRefundForCancelledEvent_should_refundAndCancelBooking_when_paymentIsCompleted() {
		payment.setStatus(PaymentStatus.COMPLETED);
		when(paymentGateway.refundTransaction(anyString())).thenReturn(true);
		when(paymentRepository.findById(paymentId))
				.thenReturn(Optional.ofNullable(payment));

		paymentService.processRefundForCancelledEvent(payment.getId());

		assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
		assertNotNull(payment.getPaidAt());

		verify(paymentGateway).refundTransaction(transferReference);
		verify(paymentRepository).save(payment);
		verify(bookingService).cancelRefundedBooking(booking.getId());
	}

	@Test
	public void processRefundForCancelledEvent_should_doNothing_when_paymentIsNotCompleted() {
		payment.setStatus(PaymentStatus.PENDING);
		when(paymentRepository.findById(paymentId))
				.thenReturn(Optional.of(payment));

		paymentService.processRefundForCancelledEvent(payment.getId());

		assertEquals(PaymentStatus.PENDING, payment.getStatus());

		verify(paymentGateway, never()).refundTransaction(anyString());
		verify(paymentRepository, never()).save(any(Payment.class));
		verify(bookingService, never()).cancelRefundedBooking(anyLong());
	}

	@Test
	public void processRefundForCancelledEvent_should_throwPaymentFailedException_when_gatewayFails() {
		payment.setStatus(PaymentStatus.COMPLETED);
		when(paymentGateway.refundTransaction(anyString())).thenReturn(false);
		when(paymentRepository.findById(paymentId))
				.thenReturn(Optional.of(payment));

		assertThrows(PaymentFailedException.class, () ->
				paymentService.processRefundForCancelledEvent(payment.getId())
		);

		assertEquals(PaymentStatus.COMPLETED, payment.getStatus());

		verify(paymentGateway).refundTransaction(transferReference);
		verify(paymentRepository, never()).save(any(Payment.class));
		verify(bookingService, never()).cancelRefundedBooking(anyLong());
	}
}
