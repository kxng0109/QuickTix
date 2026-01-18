package io.github.kxng0109.quicktix.repositories;

import io.github.kxng0109.quicktix.entity.Seat;
import io.github.kxng0109.quicktix.enums.SeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {
    long countByEventIdAndSeatStatus(Long eventId, SeatStatus seatStatus);
}
