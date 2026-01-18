package io.github.kxng0109.quicktix.repositories;

import io.github.kxng0109.quicktix.entity.Event;
import io.github.kxng0109.quicktix.entity.Venue;
import io.github.kxng0109.quicktix.enums.EventStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    Page<Event> findEventsByStatus(EventStatus status, Pageable pageable);

    Page<Event> findByVenue(Venue venue, Pageable pageable);

    Page<Event> findByEventDateTimeBetween(Instant eventDateTimeAfter, Instant eventDateTimeBefore, Pageable pageable);
}
