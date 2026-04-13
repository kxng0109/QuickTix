package io.github.kxng0109.quicktix.repositories;

import io.github.kxng0109.quicktix.entity.Payment;
import io.github.kxng0109.quicktix.enums.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
	Optional<Payment> findByBookingId(Long bookingId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Payment p where p.id=:paymentId")
	Optional<Payment> findByIdAndLock(@Param("paymentId") Long paymentId);

	Optional<Payment> findByTransactionReference(String transactionReference);

	Optional<Payment> findByIdempotencyKey(String idempotencyKey);

	//Using it to get all payments for an event in order to do something like
	//issue a refund for canceled events
	List<Payment> findByBooking_EventIdAndStatus(Long eventId, PaymentStatus status);

	@Query("SELECT SUM(p.amount) FROM Payment p WHERE p.status = :status")
	BigDecimal calculateTotalRevenue(@Param("status") PaymentStatus status);
}
