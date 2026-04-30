package io.github.kxng0109.quicktix.service;

import io.github.kxng0109.quicktix.dto.request.*;
import io.github.kxng0109.quicktix.dto.request.projection.EventSeatCount;
import io.github.kxng0109.quicktix.dto.response.EventResponse;
import io.github.kxng0109.quicktix.entity.*;
import io.github.kxng0109.quicktix.enums.EventStatus;
import io.github.kxng0109.quicktix.enums.SeatStatus;
import io.github.kxng0109.quicktix.event.EventCancelledEvent;
import io.github.kxng0109.quicktix.exception.InvalidOperationException;
import io.github.kxng0109.quicktix.exception.ResourceInUseException;
import io.github.kxng0109.quicktix.repositories.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventService {

	private final EventRepository eventRepository;
	private final VenueRepository venueRepository;
	private final SeatRepository seatRepository;
	private final ApplicationEventPublisher applicationEventPublisher;
	private final SectionRepository sectionRepository;
	private final RowRepository rowRepository;

	@Transactional
	public EventResponse createEvent(CreateEventRequest request) {
		Venue venue = venueRepository.findById(request.venueId())
		                             .orElseThrow(
				                             () -> new EntityNotFoundException("Venue not found")
		                             );

		Event event = Event.builder()
		                   .name(request.name())
		                   .description(request.description())
		                   .venue(venue)
		                   .eventStartDateTime(request.eventStartDateTime())
		                   .eventEndDateTime(request.eventEndDateTime())
		                   .status(EventStatus.UPCOMING)
		                   .build();

		Event savedEvent = eventRepository.save(event);

		List<Seat> seats = new ArrayList<>();

		for (SectionRequest sectionRequest : request.sections()) {
			Section section = Section.builder()
			                         .name(sectionRequest.name())
			                         .description(sectionRequest.description())
			                         .capacity(sectionRequest.capacity())
			                         .price(sectionRequest.basePrice())
			                         .event(savedEvent)
			                         .build();

			Section savedSection = sectionRepository.save(section);

			for(RowRequest rowRequest : sectionRequest.rows()){
				Row row = Row.builder()
						.name(rowRequest.name())
						     .rowOrder(rowRequest.rowOrder())
						     .section(savedSection)
						     .build();

				Row savedRow = rowRepository.save(row);

				for (int i = 1; i <= rowRequest.numberOfSeats(); i++) {
					Seat seat = Seat.builder()
					                .event(savedEvent)
					                .seatNumber(i)
							.row(savedRow)
					                .seatStatus(SeatStatus.AVAILABLE)
							.price(sectionRequest.basePrice())
					                .build();
					seats.add(seat);
				}
			}
		}

		seatRepository.saveAll(seats);
		savedEvent.setSeats(seats);

		return EventResponse
				.builder()
				.id(savedEvent.getId())
				.name(savedEvent.getName())
				.description(savedEvent.getDescription())
				.venueName(savedEvent.getVenue().getName())
				.ticketPrice(savedEvent.getSeats().getFirst().getPrice())
				.status(savedEvent.getStatus().getDisplayName())
				.availableSeats(seatRepository.countByEventIdAndSeatStatus(savedEvent.getId(), SeatStatus.AVAILABLE))
				.build();
	}

	@Transactional(readOnly = true)
	@Cacheable(value = "events", key = "#id", sync = true)
	public EventResponse getEventById(Long id) {
		Event event = eventRepository.findById(id)
		                             .orElseThrow(
				                             () -> new EntityNotFoundException("Event not found")
		                             );

		long availableSeats = seatRepository.countByEventIdAndSeatStatus(event.getId(), SeatStatus.AVAILABLE);

		return buildEventResponse(availableSeats, event);
	}

	@Transactional(readOnly = true)
	public Page<EventResponse> getAllUpcomingEvents(Pageable pageable) {
		Page<Event> events = eventRepository.findEventsByStatus(EventStatus.UPCOMING, pageable);

		return buildEventResponsePage(events);
	}

	@Transactional(readOnly = true)
	public Page<EventResponse> getEventsByVenueId(Long venueId, Pageable pageable) {
		Venue venue = venueRepository.findById(venueId)
		                             .orElseThrow(
				                             () -> new EntityNotFoundException("Venue not found")
		                             );

		Page<Event> events = eventRepository.findByVenue(venue, pageable);

		return buildEventResponsePage(events);
	}

	@Transactional(readOnly = true)
	public Page<EventResponse> getEventsByDateRange(EventDateSearchRequest request, Pageable pageable) {
		Page<Event> events = eventRepository.findByEventStartDateTimeBetween(request.startDate(), request.endDate(),
		                                                                     pageable
		);

		return buildEventResponsePage(events);
	}

	@Transactional
	@CacheEvict(value = "events", allEntries = true)
	public EventResponse updateEventById(Long id, UpdateEventRequest request) {
		Event event = eventRepository.findById(id)
		                             .orElseThrow(
				                             () -> new EntityNotFoundException("Event not found")
		                             );

		event.setName(request.name());
		event.setDescription(request.description());
		event.setEventStartDateTime(request.eventStartDateTime());
		event.setEventEndDateTime(request.eventEndDateTime());

		Event savedEvent = eventRepository.save(event);

		long availableSeats = seatRepository.countByEventIdAndSeatStatus(
				event.getId(),
				SeatStatus.AVAILABLE
		);

		return buildEventResponse(availableSeats, savedEvent);
	}

	@Transactional
	@CacheEvict(value = "events", allEntries = true)
	public void cancelEventById(Long eventId) {
		Event event = eventRepository.findById(eventId)
		                             .orElseThrow(
				                             () -> new EntityNotFoundException("Event not found")
		                             );

		if (event.getStatus() == EventStatus.COMPLETED || event.getStatus() == EventStatus.CANCELLED) {
			throw new InvalidOperationException("Cannot cancel an event that is already" + event.getStatus());
		}

		event.setStatus(EventStatus.CANCELLED);
		eventRepository.save(event);

		applicationEventPublisher.publishEvent(new EventCancelledEvent(eventId));
	}

	@Transactional
	@CacheEvict(value = "events", allEntries = true)
	public void deleteEventById(Long id) {
		Event event = eventRepository.findById(id)
		                             .orElseThrow(() -> new EntityNotFoundException("Event not found"));

		//We don't want to delete the event if people have already booked for the event
		//Instead we'll need to cancel and refund people back, but this logic won't handle that
		boolean hasBookings = event.getBookings() != null && !event.getBookings().isEmpty();
		if (hasBookings) {
			throw new ResourceInUseException("Cannot delete event with active bookings. Cancel the event instead.");
		}

		// Will cascade to Seats automatically
		eventRepository.delete(event);
	}

	@Transactional
	@CacheEvict(value = "events", allEntries = true)
	public void updateEventStatus() {
		List<Event> startedUpcomingEvents = eventRepository.findStartedEvent(
				EventStatus.UPCOMING,
				Instant.now()
		);

		for (Event event : startedUpcomingEvents) {
			event.setStatus(EventStatus.ONGOING);
		}
		eventRepository.saveAll(startedUpcomingEvents);

		List<Event> finishedOngoingOrUpcomingEvents = eventRepository.findEventsToComplete(
				List.of(EventStatus.UPCOMING, EventStatus.ONGOING),
				Instant.now()
		);

		for (Event event : finishedOngoingOrUpcomingEvents) {
			event.setStatus(EventStatus.COMPLETED);
		}
		eventRepository.saveAll(finishedOngoingOrUpcomingEvents);
	}


	private EventResponse buildEventResponse(Long numberOfAvailableSeats, Event event) {
		return EventResponse
				.builder()
				.id(event.getId())
				.name(event.getName())
				.description(event.getDescription())
				.venueName(event.getVenue().getName())
				.ticketPrice(event.getSeats().getFirst().getPrice())
				.status(event.getStatus().getDisplayName())
				.availableSeats(numberOfAvailableSeats)
				.eventStartDateTime(event.getEventStartDateTime())
				.eventEndDateTime(event.getEventEndDateTime())
				.build();
	}

	private Page<EventResponse> buildEventResponsePage(Page<Event> eventsPage) {
		if (eventsPage == null) return null;

		//Extract the ID from eventsPage
		List<Long> eventIds = eventsPage.stream()
		                                .map(Event::getId)
		                                .toList();

		//Convert them to a map
		Map<Long, Long> eventSeatCountsList = seatRepository
				.countAvailableSeatsByEventIds(eventIds, SeatStatus.AVAILABLE)
				.stream()
				.collect(
						Collectors.toMap(
								EventSeatCount::getEventId,
								EventSeatCount::getAvailableSeats
						)
				);

		return eventsPage.map(
				event -> buildEventResponse(eventSeatCountsList.get(event.getId()), event)
		);
	}
}
