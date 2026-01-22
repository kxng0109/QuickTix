package io.github.kxng0109.quicktix.service;

import io.github.kxng0109.quicktix.dto.request.PaymentRequest;
import io.github.kxng0109.quicktix.dto.response.PaymentResponse;
import io.github.kxng0109.quicktix.entity.Booking;
import io.github.kxng0109.quicktix.entity.Payment;
import io.github.kxng0109.quicktix.enums.BookingStatus;
import io.github.kxng0109.quicktix.enums.PaymentStatus;
import io.github.kxng0109.quicktix.exception.InvalidAmountException;
import io.github.kxng0109.quicktix.exception.InvalidOperationException;
import io.github.kxng0109.quicktix.exception.PaymentFailedException;
import io.github.kxng0109.quicktix.repositories.BookingRepository;
import io.github.kxng0109.quicktix.repositories.PaymentRepository;
import io.github.kxng0109.quicktix.service.gateway.PaymentGateway;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

	private final PaymentRepository paymentRepository;
	private final BookingRepository bookingRepository;
	private final BookingService bookingService;
	private final PaymentGateway paymentGateway;

	@Transactional(readOnly = true)
	public PaymentResponse getPaymentByBookingId(Long bookingId) {
		Payment payment = paymentRepository.findByBookingId(bookingId)
		                                   .orElseThrow(
				                                   () -> new EntityNotFoundException("Payment not found")
		                                   );

		return buildPaymentResponse(payment);
	}

	//Its job is just to start the payment,
	// particularly creating and saving the Payment object.
	@Transactional
	public PaymentResponse initializePayment(PaymentRequest request) {
		Booking booking = bookingRepository.findById(request.bookingId())
		                                   .orElseThrow(
				                                   () -> new EntityNotFoundException("Booking not found")
		                                   );

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
		return buildPaymentResponse(savedPayment);
	}

	@Transactional
	public PaymentResponse verifyPayment(String transactionReference) {
		Payment payment = paymentRepository.findByTransactionReference(transactionReference)
		                                   .orElseThrow(
				                                   () -> new EntityNotFoundException("Payment not found")
		                                   );

		if (payment.getStatus() == PaymentStatus.COMPLETED) {
			return buildPaymentResponse(payment);
		}

		boolean isPaymentSuccessful = paymentGateway.verifyTransaction(transactionReference);

		if (isPaymentSuccessful) {
			payment.setStatus(PaymentStatus.COMPLETED);
			payment.setPaidAt(Instant.now());
			paymentRepository.save(payment);

			bookingService.confirmBooking(payment.getBooking().getId());
		} else {
			payment.setStatus(PaymentStatus.FAILED);
			paymentRepository.save(payment);
			throw new PaymentFailedException("Payment verification failed");
		}

		return buildPaymentResponse(payment);
	}

	@Transactional
	public void refundPayment(Long bookingId) {
		Booking booking = bookingRepository.findById(bookingId)
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
	 * @param payment the {@link Payment payment} to be refunded
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void processRefundForCancelledEvent(Payment payment) {
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
