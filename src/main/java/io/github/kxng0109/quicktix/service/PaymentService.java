package io.github.kxng0109.quicktix.service;

import io.github.kxng0109.quicktix.dto.request.PaymentRequest;
import io.github.kxng0109.quicktix.dto.request.message.NotificationRequest;
import io.github.kxng0109.quicktix.dto.response.PaymentResponse;
import io.github.kxng0109.quicktix.entity.Booking;
import io.github.kxng0109.quicktix.entity.Payment;
import io.github.kxng0109.quicktix.entity.User;
import io.github.kxng0109.quicktix.enums.BookingStatus;
import io.github.kxng0109.quicktix.enums.PaymentStatus;
import io.github.kxng0109.quicktix.exception.InvalidAmountException;
import io.github.kxng0109.quicktix.exception.InvalidOperationException;
import io.github.kxng0109.quicktix.exception.PaymentFailedException;
import io.github.kxng0109.quicktix.repositories.BookingRepository;
import io.github.kxng0109.quicktix.repositories.PaymentRepository;
import io.github.kxng0109.quicktix.service.gateway.PaymentGateway;
import io.github.kxng0109.quicktix.utils.AssertOwnershipOrAdmin;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

	private final PaymentRepository paymentRepository;
	private final BookingRepository bookingRepository;
	private final BookingService bookingService;
	private final PaymentGateway paymentGateway;
	private final NotificationPublisherService notificationPublisherService;

	@Transactional(readOnly = true)
	public PaymentResponse getPaymentByBookingId(Long bookingId, User currentUser) {
		Payment payment = paymentRepository.findByBookingId(bookingId)
		                                   .orElseThrow(
				                                   () -> new EntityNotFoundException("Payment not found")
		                                   );

		AssertOwnershipOrAdmin.check(currentUser, payment.getBooking().getUser());

		return buildPaymentResponse(payment);
	}

	//Its job is just to start the payment,
	// particularly creating and saving the Payment object.
	@Transactional
	public PaymentResponse initializePayment(PaymentRequest request, User currentUser) {
		Booking booking = bookingRepository.findById(request.bookingId())
		                                   .orElseThrow(
				                                   () -> new EntityNotFoundException("Booking not found")
		                                   );

		if (!booking.getUser().getId().equals(currentUser.getId())) {
			throw new EntityNotFoundException("Booking not found");
		}

		if (booking.getStatus() != BookingStatus.PENDING) {
			throw new InvalidOperationException("Booking status must be PENDING");
		}

		BigDecimal totalAmount = booking.getTotalAmount();
		if (totalAmount.compareTo(request.amount()) != 0) {
			throw new InvalidAmountException("Payment amount mismatch");
		}

		String transferReference = UUID.randomUUID().toString();
		Payment payment = Payment.builder()
		                         .booking(booking)
		                         .amount(totalAmount)
		                         .status(PaymentStatus.PENDING)
		                         .paymentMethod(request.paymentMethod())
		                         .transactionReference(transferReference)
		                         .build();

		Payment savedPayment = paymentRepository.save(payment);

		String gatewayToken = paymentGateway.initializePayment(savedPayment);

		return PaymentResponse.builder()
		                      .paymentId(savedPayment.getId())
		                      .amount(savedPayment.getAmount())
		                      .status(savedPayment.getStatus().getDisplayName())
		                      .paymentMethod(savedPayment.getPaymentMethod().getDisplayName())
		                      .paidAt(savedPayment.getPaidAt())
		                      .clientSecret(gatewayToken)
		                      .build();
	}

	/**
	 * Asynchronously confirms a payment based on a successful webhook event from Stripe.
	 * <p>
	 * This method acts as the absolute source of truth for payment success. It updates
	 * the payment status, records the external transaction reference, and triggers
	 * the booking confirmation process.
	 * <p>
	 * <b>Idempotency:</b> If the payment is already marked as {@code COMPLETED},
	 * this method immediately returns to prevent duplicate processing if Stripe
	 * sends duplicate webhook events.
	 *
	 * @param paymentId             the internal database ID of the payment, extracted from Stripe metadata.
	 * @param stripePaymentIntentId the unique ID of the successful Stripe PaymentIntent.
	 * @throws jakarta.persistence.EntityNotFoundException if the payment ID does not exist in the database.
	 */
	@Transactional
	public void handleSuccessfulWebhookPayment(Long paymentId, String stripePaymentIntentId) {
		Payment payment = paymentRepository.findByBookingId(paymentId)
		                                   .orElseThrow(
				                                   () -> new EntityNotFoundException("Payment not found from webhook")
		                                   );

		if (payment.getStatus() == PaymentStatus.COMPLETED) return;

		payment.setStatus(PaymentStatus.COMPLETED);
		payment.setTransactionReference(stripePaymentIntentId);
		payment.setPaidAt(Instant.now());
		paymentRepository.save(payment);

		bookingService.confirmBooking(payment.getBooking().getId());
		log.info("Webhook successfully processed payment ID: {}", paymentId);

		// Send the email task to NotifyHub
		NotificationRequest receipt = NotificationRequest.builder()
		                                                 .to(List.of(payment.getBooking().getUser()
		                                                                    .getEmail())) // NotifyHub expects a List
		                                                 .subject(
				                                                 "Your QuickTix Booking Confirmation: " + payment.getBooking()
				                                                                                                 .getBookingReference())
		                                                 .htmlBody(
				                                                 "<h1>Payment Successful!</h1><p>Your seats are confirmed for " + payment.getBooking()
				                                                                                                                         .getEvent()
				                                                                                                                         .getName() + ".</p>")
		                                                 .build();

		notificationPublisherService.publishNotification(receipt);
	}

	@Transactional
	public void refundPayment(Long bookingId) {
		bookingRepository.findById(bookingId)
		                 .orElseThrow(
				                 () -> new EntityNotFoundException("Booking not found")
		                 );

		Payment payment = paymentRepository.findByBookingId(bookingId)
		                                   .orElseThrow(
				                                   () -> new EntityNotFoundException("Payment not found")
		                                   );

		if (payment.getStatus() == PaymentStatus.REFUNDED) {
			throw new InvalidOperationException("Payment has already been refunded");
		}

		if (payment.getStatus() != PaymentStatus.COMPLETED) {
			throw new InvalidOperationException("Cannot refund payment if payment was not completed.");
		}

		boolean refundSuccess = paymentGateway.refundTransaction(payment.getTransactionReference());

		if (!refundSuccess) {
			throw new PaymentFailedException("Refund failed at gateway.");
		}

		payment.setStatus(PaymentStatus.REFUNDED);
		payment.setPaidAt(Instant.now());
		paymentRepository.save(payment);

		bookingService.cancelRefundedBooking(bookingId);
	}

	/**
	 * It handles refunds for cancelled events only for those who paid.
	 * It processes a single payment at a time.
	 * It uses {@link Propagation#REQUIRES_NEW} so one failed transaction doesn't rollback the rest.
	 * They commit independently.
	 *
	 * @param paymentId the id of the {@link Payment payment} to be refunded
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void processRefundForCancelledEvent(Long paymentId) {
		Payment payment = paymentRepository.findById(paymentId)
				.orElseThrow(() -> new EntityNotFoundException("Payment not found!"));
		if (payment.getStatus() != PaymentStatus.COMPLETED) return;

		log.debug("Processing refund for Payment ID: {}", payment.getId());

		boolean success = paymentGateway.refundTransaction(payment.getTransactionReference());

		if (!success) {
			throw new PaymentFailedException("Refund failed at gateway.");
		}

		payment.setStatus(PaymentStatus.REFUNDED);
		payment.setPaidAt(Instant.now());
		paymentRepository.save(payment);

		bookingService.cancelRefundedBooking(payment.getBooking().getId());

		log.debug("Successfully refunded Payment ID: {}", payment.getId());
	}

	private PaymentResponse buildPaymentResponse(Payment payment) {
		return PaymentResponse.builder()
		                      .paymentId(payment.getId())
		                      .amount(payment.getAmount())
		                      .status(payment.getStatus().getDisplayName())
		                      .paymentMethod(payment.getPaymentMethod().getDisplayName())
		                      .paidAt(payment.getPaidAt())
		                      .build();
	}
}
