package io.github.kxng0109.quicktix.repositories;

import io.github.kxng0109.quicktix.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByBookingId(Long bookingId);

    Optional<Payment> findByTransactionReference(String transactionReference);
}
