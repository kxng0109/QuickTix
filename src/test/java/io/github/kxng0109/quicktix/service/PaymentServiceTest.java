package io.github.kxng0109.quicktix.service;

import io.github.kxng0109.quicktix.dto.request.PaymentRequest;
import io.github.kxng0109.quicktix.dto.response.PaymentResponse;
import io.github.kxng0109.quicktix.entity.Booking;
import io.github.kxng0109.quicktix.entity.Payment;
import io.github.kxng0109.quicktix.entity.User;
import io.github.kxng0109.quicktix.enums.BookingStatus;
import io.github.kxng0109.quicktix.enums.PaymentMethod;
import io.github.kxng0109.quicktix.enums.PaymentStatus;
import io.github.kxng0109.quicktix.exception.InvalidAmountException;
import io.github.kxng0109.quicktix.exception.InvalidOperationException;
import io.github.kxng0109.quicktix.exception.PaymentFailedException;
import io.github.kxng0109.quicktix.repositories.BookingRepository;
import io.github.kxng0109.quicktix.repositories.PaymentRepository;
import io.github.kxng0109.quicktix.service.gateway.PaymentGateway;
import io.github.kxng0109.quicktix.utils.BookingReferenceGenerator;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
                                                         .amount(totalAmount)
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

    @InjectMocks
    private PaymentService paymentService;

    private Payment payment;
    private Booking booking;

    @BeforeEach
    void setUp() {
        booking = Booking.builder()
                         .id(bookingId)
                         .status(bookingStatus)
                         .bookingReference(BookingReferenceGenerator.generate())
                         .totalAmount(totalAmount)
                         .user(User.builder().id(300L).build())
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
    }

    @Test
    public void getPaymentByBookingId_should_returnPaymentResponse_when_paymentIsFound() {
        when(paymentRepository.findByBookingId(anyLong()))
                .thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.getPaymentByBookingId(paymentId);

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
                () -> paymentService.getPaymentByBookingId(paymentId)
        );

        verify(paymentRepository).findByBookingId(anyLong());
    }

    @Test
    public void initializePayment_should_returnAPaymentResponse_when_requestIsValid() {
        when(bookingRepository.findById(anyLong()))
                .thenReturn(Optional.of(booking));
        when(paymentRepository.save(any(Payment.class)))
                .thenReturn(payment);

        PaymentResponse response = paymentService.initializePayment(request);

        assertNotNull(response);
        assertEquals(paymentStatus.getDisplayName(), response.status());
        assertEquals(paymentMethod.getDisplayName(), response.paymentMethod());
        assertEquals(paymentId, response.paymentId());

        verify(bookingRepository).findById(anyLong());
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    public void initializePayment_should_throwEntityNotFoundException_when_bookingIsNotFound() {
        when(bookingRepository.findById(anyLong()))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> paymentService.initializePayment(request)
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
                () -> paymentService.initializePayment(request)
        );

        verify(bookingRepository).findById(anyLong());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    public void initializePayment_should_throwInvalidAmountException_when_paymentAmountMismatch() {
        booking.setTotalAmount(totalAmount.add(BigDecimal.valueOf(10)));

        when(bookingRepository.findById(anyLong()))
                .thenReturn(Optional.of(booking));

        InvalidAmountException ex = assertThrows(
                InvalidAmountException.class,
                () -> paymentService.initializePayment(request)
        );

        assertEquals("Payment amount mismatch", ex.getMessage());
        assertEquals(1, booking.getTotalAmount().compareTo(totalAmount));

        verify(bookingRepository).findById(anyLong());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    public void verifyPayment_should_returnPaymentResponseAndMarkPaymentAsCompleted_when_paymentIsSuccessful() {
        when(paymentRepository.findByTransactionReference(anyString()))
                .thenReturn(Optional.of(payment));
        when(paymentGateway.verifyTransaction(anyString()))
                .thenReturn(true);

        PaymentResponse response = paymentService.verifyPayment(transferReference);

        assertNotNull(response);
        assertEquals(PaymentStatus.COMPLETED.getDisplayName(), response.status());
        assertEquals(paymentId, response.paymentId());

        verify(paymentRepository).findByTransactionReference(anyString());
        verify(paymentGateway).verifyTransaction(anyString());
        verify(paymentRepository).save(any(Payment.class));
        verify(bookingService).confirmBooking(anyLong());
    }

    @Test
    public void verifyPayment_should_returnPaymentResponse_when_paymentIsAlreadySuccessful() {
        payment.setStatus(PaymentStatus.COMPLETED);

        when(paymentRepository.findByTransactionReference(anyString()))
                .thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.verifyPayment(transferReference);

        assertNotNull(response);
        assertEquals(PaymentStatus.COMPLETED.getDisplayName(), response.status());
        assertEquals(paymentId, response.paymentId());

        verify(paymentRepository).findByTransactionReference(anyString());
        verify(paymentGateway, never()).verifyTransaction(anyString());
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(bookingService, never()).confirmBooking(anyLong());
    }

    @Test
    public void verifyPayment_should_throwEntityNotFoundException_when_paymentIsNotFound() {
        when(paymentRepository.findByTransactionReference(anyString()))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> paymentService.verifyPayment(transferReference)
        );

        verify(paymentRepository).findByTransactionReference(anyString());
        verify(paymentGateway, never()).verifyTransaction(anyString());
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(bookingService, never()).confirmBooking(anyLong());
    }

    @Test
    public void verifyPayment_should_throwPaymentFailedException_when_paymentFails() {
        when(paymentRepository.findByTransactionReference(anyString()))
                .thenReturn(Optional.of(payment));
        when(paymentGateway.verifyTransaction(anyString()))
                .thenReturn(false);

        assertThrows(
                PaymentFailedException.class,
                () -> paymentService.verifyPayment(transferReference)
        );

        assertEquals(PaymentStatus.FAILED, payment.getStatus());

        verify(paymentRepository).findByTransactionReference(anyString());
        verify(paymentGateway).verifyTransaction(anyString());
        verify(paymentRepository).save(any(Payment.class));
        verify(bookingService, never()).confirmBooking(anyLong());
    }

    @Test
    public void refundPayment_should_refundPaymentAndReturnNothing_when_paymentStatusIsCompleted() {
        payment.setStatus(PaymentStatus.COMPLETED);

        when(bookingRepository.findById(anyLong()))
                .thenReturn(Optional.of(booking));
        when(paymentRepository.findByBookingId(anyLong()))
                .thenReturn(Optional.of(payment));
        when(paymentGateway.refundTransaction(anyString()))
                .thenReturn(true);

        paymentService.refundPayment(bookingId);

        assertEquals(PaymentStatus.REFUNDED, payment.getStatus());

        verify(bookingRepository).findById(anyLong());
        verify(paymentRepository).findByBookingId(anyLong());
        verify(paymentGateway).refundTransaction(anyString());
        verify(paymentRepository).save(any(Payment.class));
        verify(bookingService).cancelBooking(anyLong(), anyLong());
    }

    @Test
    public void refundPayment_should_throwEntityNotFoundException_when_bookingIsNotFound() {
        payment.setStatus(PaymentStatus.COMPLETED);

        when(bookingRepository.findById(anyLong()))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> paymentService.refundPayment(bookingId)
        );

        assertEquals(PaymentStatus.COMPLETED, payment.getStatus());

        verify(bookingRepository).findById(anyLong());
        verify(paymentRepository, never()).findByBookingId(anyLong());
        verify(paymentGateway, never()).refundTransaction(anyString());
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(bookingService, never()).cancelBooking(anyLong(), anyLong());
    }

    @Test
    public void refundPayment_should_throwEntityNotFoundException_when_paymentIsNotFound() {
        payment.setStatus(PaymentStatus.COMPLETED);

        when(bookingRepository.findById(anyLong()))
                .thenReturn(Optional.of(booking));
        when(paymentRepository.findByBookingId(anyLong()))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> paymentService.refundPayment(bookingId)
        );

        assertEquals(PaymentStatus.COMPLETED, payment.getStatus());

        verify(bookingRepository).findById(anyLong());
        verify(paymentRepository).findByBookingId(anyLong());
        verify(paymentGateway, never()).refundTransaction(anyString());
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(bookingService, never()).cancelBooking(anyLong(), anyLong());
    }

    @Test
    public void refundPayment_should_throwInvalidOperationException_when_paymentStatusIsRefunded() {
        payment.setStatus(PaymentStatus.REFUNDED);

        when(bookingRepository.findById(anyLong()))
                .thenReturn(Optional.of(booking));
        when(paymentRepository.findByBookingId(anyLong()))
                .thenReturn(Optional.of(payment));

        InvalidOperationException ex = assertThrows(
                InvalidOperationException.class,
                () -> paymentService.refundPayment(bookingId)
        );

        assertEquals("Payment has already been refunded", ex.getMessage());
        assertEquals(PaymentStatus.REFUNDED, payment.getStatus());

        verify(bookingRepository).findById(anyLong());
        verify(paymentRepository).findByBookingId(anyLong());
        verify(paymentGateway, never()).refundTransaction(anyString());
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(bookingService, never()).cancelBooking(anyLong(), anyLong());
    }

    @Test
    public void refundPayment_should_throwInvalidOperationException_when_paymentStatusIsPending() {
        when(bookingRepository.findById(anyLong()))
                .thenReturn(Optional.of(booking));
        when(paymentRepository.findByBookingId(anyLong()))
                .thenReturn(Optional.of(payment));

        InvalidOperationException ex = assertThrows(
                InvalidOperationException.class,
                () -> paymentService.refundPayment(bookingId)
        );

        assertEquals("Cannot refund payment if payment was not completed.", ex.getMessage());
        assertEquals(PaymentStatus.PENDING, payment.getStatus());

        verify(bookingRepository).findById(anyLong());
        verify(paymentRepository).findByBookingId(anyLong());
        verify(paymentGateway, never()).refundTransaction(anyString());
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(bookingService, never()).cancelBooking(anyLong(), anyLong());
    }

    @Test
    public void refundPayment_should_throwInvalidOperationException_when_paymentStatusIsFailed() {
        payment.setStatus(PaymentStatus.FAILED);

        when(bookingRepository.findById(anyLong()))
                .thenReturn(Optional.of(booking));
        when(paymentRepository.findByBookingId(anyLong()))
                .thenReturn(Optional.of(payment));

        InvalidOperationException ex = assertThrows(
                InvalidOperationException.class,
                () -> paymentService.refundPayment(bookingId)
        );

        assertEquals("Cannot refund payment if payment was not completed.", ex.getMessage());
        assertEquals(PaymentStatus.FAILED, payment.getStatus());

        verify(bookingRepository).findById(anyLong());
        verify(paymentRepository).findByBookingId(anyLong());
        verify(paymentGateway, never()).refundTransaction(anyString());
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(bookingService, never()).cancelBooking(anyLong(), anyLong());
    }

    @Test
    public void refundPayment_should_throwPaymentFailedException_when_refundFailed() {
        payment.setStatus(PaymentStatus.COMPLETED);

        when(bookingRepository.findById(anyLong()))
                .thenReturn(Optional.of(booking));
        when(paymentRepository.findByBookingId(anyLong()))
                .thenReturn(Optional.of(payment));
        when(paymentGateway.refundTransaction(anyString()))
                .thenReturn(false);

        PaymentFailedException ex = assertThrows(
                PaymentFailedException.class,
                () -> paymentService.refundPayment(bookingId)
        );

        assertEquals("Refund failed at gateway.", ex.getMessage());
        assertEquals(PaymentStatus.COMPLETED, payment.getStatus());

        verify(bookingRepository).findById(anyLong());
        verify(paymentRepository).findByBookingId(anyLong());
        verify(paymentGateway).refundTransaction(anyString());
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(bookingService, never()).cancelBooking(anyLong(), anyLong());
    }
}
