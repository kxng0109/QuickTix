package io.github.kxng0109.quicktix.repositories;

import io.github.kxng0109.quicktix.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
}
