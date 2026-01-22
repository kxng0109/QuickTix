package io.github.kxng0109.quicktix.repositories;

import io.github.kxng0109.quicktix.entity.Seat;
import io.github.kxng0109.quicktix.enums.SeatStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {
	Page<Seat> findByEventId(Long eventId, Pageable pageable);

	long countByEventIdAndSeatStatus(Long eventId, SeatStatus seatStatus);

	Page<Seat> findByEventIdAndSeatStatus(Long eventId, SeatStatus seatStatus, Pageable pageable);

	List<Seat> findBySeatStatusAndHeldAtBefore(SeatStatus seatStatus, Instant cutoffTime);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT s FROM Seat s WHERE s.id IN :seatIds")
	List<Seat> findAllByIdWithLock(@Param("seatIds") List<Long> seatIds);
}
