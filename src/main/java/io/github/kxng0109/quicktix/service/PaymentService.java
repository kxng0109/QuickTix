package io.github.kxng0109.quicktix.service;

import io.github.kxng0109.quicktix.dto.request.PaymentRequest;
import io.github.kxng0109.quicktix.dto.request.message.NotificationRequest;
import io.github.kxng0109.quicktix.dto.response.PaymentResponse;
import io.github.kxng0109.quicktix.entity.Booking;
import io.github.kxng0109.quicktix.entity.Payment;
import io.github.kxng0109.quicktix.entity.User;
import io.github.kxng0109.quicktix.enums.BookingStatus;
import io.github.kxng0109.quicktix.enums.PaymentStatus;
import io.github.kxng0109.quicktix.enums.Role;
import io.github.kxng0109.quicktix.exception.ConflictException;
import io.github.kxng0109.quicktix.exception.InvalidAmountException;
import io.github.kxng0109.quicktix.exception.InvalidOperationException;
import io.github.kxng0109.quicktix.exception.PaymentFailedException;
import io.github.kxng0109.quicktix.repositories.BookingRepository;
import io.github.kxng0109.quicktix.repositories.PaymentRepository;
import io.github.kxng0109.quicktix.service.gateway.PaymentGateway;
import io.github.kxng0109.quicktix.service.gateway.dto.GatewayInitializationResponse;
import io.github.kxng0109.quicktix.utils.AssertOwnershipOrAdmin;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

	private final PaymentRepository paymentRepository;
	private final BookingRepository bookingRepository;
	private final BookingService bookingService;
	private final PaymentGateway paymentGateway;
	private final NotificationPublisherService notificationPublisherService;
	private final StringRedisTemplate stringRedisTemplate;

	@Transactional(readOnly = true)
	public PaymentResponse getPaymentByBookingId(Long bookingId, User currentUser) {
		Payment payment = paymentRepository.findByBookingId(bookingId)
		                                   .orElseThrow(
				                                   () -> new EntityNotFoundException("Payment not found")
		                                   );

		AssertOwnershipOrAdmin.check(currentUser, payment.getBooking().getUser());

		return buildPaymentResponse(payment, null);
	}

	//Its job is just to start the payment,
	// particularly creating and saving the Payment object.
	/**
	 * Initializes a payment sequence for a pending booking.
	 * <p>
	 * Verifies that the payment amount matches the secure backend calculation of the booking,
	 * creates a {@link Payment} record in a {@code PENDING} state, and delegates to the
	 * external {@link PaymentGateway} to generate a client secret / checkout URL.
	 * </p>
	 *
	 * @param request     The payload containing the booking ID and payment method.
	 * @param currentUser The authenticated user initiating the payment.
	 * @return A {@link PaymentResponse} containing the gateway's client secret.
	 */
	@Transactional
	public PaymentResponse initializePayment(PaymentRequest request, String idempotencyKey, User currentUser) {
		String normalizedIdempotencyKey = idempotencyKey.trim().toLowerCase();
		Payment existingPayment = paymentRepository.findByIdempotencyKey(normalizedIdempotencyKey)
		                                           .orElse(null);

		if (existingPayment != null) {
			validateIdempotentPaymentOwnership(existingPayment, currentUser);
			return buildPaymentResponse(existingPayment, existingPayment.getGatewayToken());
		}

		String redisLockKey = "payment:lock:" + normalizedIdempotencyKey;
		boolean acquiredLock = Objects.equals(
				Boolean.TRUE,
				stringRedisTemplate.opsForValue()
				                   .setIfAbsent(
						                   redisLockKey,
						                   "processing",
						                   Duration.ofMinutes(2)
				                   )
		);

		//Lock has already been acquired by another request
		if (!acquiredLock) {
			log.warn("Concurrent payment initialization detected for idempotency key: {}", normalizedIdempotencyKey);

			throw new ConflictException("This payment is currently being processed. Please wait.");
		}

		try {
			Booking booking = bookingRepository.findById(request.bookingId())
			                                   .orElseThrow(
					                                   () -> new EntityNotFoundException("Booking not found")
			                                   );

			validateBookingOwnership(booking, currentUser);

			if (booking.getStatus() != BookingStatus.PENDING) {
				throw new InvalidOperationException("Booking status must be PENDING");
			}

			BigDecimal totalAmount = booking.getTotalAmount();

			if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
				throw new InvalidAmountException("Amount must be greater than zero!");
			}

			Payment payment = Payment.builder()
			                         .booking(booking)
			                         .amount(totalAmount)
			                         .status(PaymentStatus.PENDING)
			                         .paymentMethod(request.paymentMethod())
			                         .idempotencyKey(normalizedIdempotencyKey)
			                         .build();

			Payment savedPayment = paymentRepository.save(payment);

			GatewayInitializationResponse gatewayToken = paymentGateway.initializePayment(savedPayment);

			savedPayment.setTransactionReference(gatewayToken.transactionId());
			savedPayment.setGatewayToken(gatewayToken.clientSecret());
			paymentRepository.save(savedPayment);

			return buildPaymentResponse(savedPayment, gatewayToken.clientSecret());
		} finally {
			stringRedisTemplate.delete(redisLockKey);
		}
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
		Payment payment = paymentRepository.findByIdAndLock(paymentId)
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

	/**
	 * Refunds a customer's completed booking and releases their reserved seats.
	 * <p>
	 * It communicates directly
	 * with the configured payment gateway (e.g., Stripe/Paystack) to reverse the financial transaction, marks
	 * the internal payment state as {@link PaymentStatus#REFUNDED}, and delegates to the
	 * {@link BookingService} to cancel the booking and free the associated seats.
	 * </p>
	 *
	 * @param bookingId The unique identifier of the booking to refund.
	 * @throws InvalidOperationException if the payment is not in a {@code COMPLETED} state.
	 * @throws PaymentFailedException    if the external payment gateway rejects the refund request.
	 * @throws EntityNotFoundException   if no payment is attached to the provided booking ID.
	 */
	@Transactional
	public void refundPayment(Long bookingId) {
		Payment payment = paymentRepository.findByIdAndLock(bookingId)
		                                   .orElseThrow(
				                                   () -> new EntityNotFoundException("Payment not found")
		                                   );

		if (payment.getStatus() == PaymentStatus.REFUNDED) {
			log.error("Booking with ID {} already refunded!", bookingId);
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
		log.info("User with booking {} refunded", bookingId);
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

	private void validateBookingOwnership(Booking booking, User currentUser) {
		if (currentUser.getRole().equals(Role.ADMIN)) return;

		if (!Objects.equals(currentUser.getId(), booking.getUser().getId())) {
			throw new EntityNotFoundException("Booking Not Found!");
		}
	}

	private void validateIdempotentPaymentOwnership(
			Payment existingPayment,
			User currentUser
	){
		if (currentUser.getRole().equals(Role.ADMIN)) return;

		if (!Objects.equals(currentUser.getId(), existingPayment.getBooking().getUser().getId())) {
			throw new EntityNotFoundException("Payment Not Found!");
		}
	}

	private PaymentResponse buildPaymentResponse(Payment payment, String clientSecret) {
		return PaymentResponse.builder()
		                      .paymentId(payment.getId())
		                      .amount(payment.getAmount())
		                      .status(payment.getStatus().getDisplayName())
		                      .paymentMethod(payment.getPaymentMethod().getDisplayName())
		                      .paidAt(payment.getPaidAt())
		                      .clientSecret(clientSecret)
		                      .build();
	}
}
