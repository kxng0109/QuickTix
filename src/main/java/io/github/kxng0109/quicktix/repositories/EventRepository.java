package io.github.kxng0109.quicktix.repositories;

import io.github.kxng0109.quicktix.entity.Event;
import io.github.kxng0109.quicktix.entity.Venue;
import io.github.kxng0109.quicktix.enums.EventStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    Page<Event> findEventsByStatus(EventStatus status, Pageable pageable);

    Page<Event> findByVenue(Venue venue, Pageable pageable);

    Page<Event> findByEventStartDateTimeBetween(Instant eventDateTimeAfter, Instant eventDateTimeBefore, Pageable pageable);

    //Using it to select events that are currently ongoing but whose status are not updated to show ONGOING
    @Query("select e from Event e where e.status = :status and e.eventStartDateTime <= :now and e.eventEndDateTime > :now")
    List<Event> findStartedEvent(@Param("status") EventStatus status, @Param("now") Instant now);

	//To find events that has finished but are still marked as completed or upcoming
	@Query("select e from Event e where e.status in :statuses and e.eventEndDateTime < :now")
	List<Event> findEventsToComplete(@Param("statuses") List<EventStatus> statuses, @Param("now") Instant now);
}
