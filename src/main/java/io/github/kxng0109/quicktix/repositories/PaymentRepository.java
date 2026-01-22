package io.github.kxng0109.quicktix.repositories;

import io.github.kxng0109.quicktix.entity.Payment;
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
	@Query("SELECT p FROM Payment p JOIN FETCH p.booking b WHERE b.event.id = :eventId AND p.status = 'COMPLETED'")
	List<Payment> findAllCompletedPaymentsForEvent(@Param("eventId") Long eventId);
}
