package io.github.kxng0109.quicktix.repositories;

import io.github.kxng0109.quicktix.dto.request.projection.EventSeatCount;
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

	/**
	 * Retrieves the count of seats matching a specific status, grouped by their associated Event IDs.
	 * <p>
	 * <b>Performance Note:</b> This method is engineered for batch fetching. By passing a list of
	 * Event IDs, it calculates the seat counts for multiple events in a single database round-trip,
	 * bypassing the N+1 query performance bottleneck.
	 * * @param eventIds A list of Event IDs to calculate seat counts for.
	 * <b>Warning:</b> Do not pass an empty list, as it will cause an SQL syntax error
	 * in the IN clause. Always check if the list is empty before calling this method.
	 * @param status   The specific {@link SeatStatus} to count (e.g., AVAILABLE).
	 * @return A list of {@link EventSeatCount} projections, mapping each requested Event ID
	 * to its respective seat count. If an event has 0 seats matching the status,
	 * it will simply not be included in the returned list.
	 */
	@Query("SELECT s.event.id AS eventId, COUNT(s.id) AS availableSeats " +
			"FROM Seat s " +
			"WHERE s.event.id IN :eventIds AND s.seatStatus = :status " +
			"GROUP BY s.event.id")
	List<EventSeatCount> countAvailableSeatsByEventIds(
			@Param("eventIds") List<Long> eventIds,
			@Param("status") SeatStatus status
	);

	Page<Seat> findByEventIdAndSeatStatus(Long eventId, SeatStatus seatStatus, Pageable pageable);

	List<Seat> findBySeatStatusAndHeldAtBefore(SeatStatus seatStatus, Instant cutoffTime);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT s FROM Seat s WHERE s.id IN :seatIds")
	List<Seat> findAllByIdWithLock(@Param("seatIds") List<Long> seatIds);
}
