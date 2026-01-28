package io.github.kxng0109.quicktix.repositories;

import io.github.kxng0109.quicktix.entity.Booking;
import io.github.kxng0109.quicktix.enums.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
	Optional<Booking> findByBookingReference(String bookingReference);

	boolean existsByBookingReference(String bookingReference);

	Page<Booking> findByUserId(Long userId, Pageable pageable);

	List<Booking> findByStatusAndCreatedAtBefore(BookingStatus status, Instant createdAt);

	@Query("SELECT b FROM Booking b LEFT JOIN FETCH b.payment WHERE b.id = :id")
	Optional<Booking> findByIdWithPayment(@Param("id") Long id);

	List<Booking> findByStatusAndEventId(BookingStatus status, Long eventId);

	@Query("SELECT b FROM Booking b LEFT JOIN FETCH b.seats WHERE b.id = :id")
	Optional<Booking> findByIdWithSeats(@Param("id") Long id);
}
