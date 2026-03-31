package io.github.kxng0109.quicktix.repositories;

import io.github.kxng0109.quicktix.entity.Payment;
import io.github.kxng0109.quicktix.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
	Optional<Payment> findByBookingId(Long bookingId);

	Optional<Payment> findByTransactionReference(String transactionReference);

	//Using it to get all payments for an event in order to do something like
	//issue a refund for canceled events
	List<Payment> findByBooking_EventIdAndStatus(Long eventId, PaymentStatus status);
}
