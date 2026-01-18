package io.github.kxng0109.quicktix.service;

import io.github.kxng0109.quicktix.dto.request.CreateEventRequest;
import io.github.kxng0109.quicktix.dto.request.EventDateSearchRequest;
import io.github.kxng0109.quicktix.dto.response.EventResponse;
import io.github.kxng0109.quicktix.entity.Event;
import io.github.kxng0109.quicktix.entity.Seat;
import io.github.kxng0109.quicktix.entity.Venue;
import io.github.kxng0109.quicktix.enums.EventStatus;
import io.github.kxng0109.quicktix.enums.SeatStatus;
import io.github.kxng0109.quicktix.repositories.EventRepository;
import io.github.kxng0109.quicktix.repositories.SeatRepository;
import io.github.kxng0109.quicktix.repositories.VenueRepository;
import io.github.kxng0109.quicktix.utils.EnumUtils;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final VenueRepository venueRepository;
    private final SeatRepository seatRepository;

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
                           .eventDateTime(request.eventDateTime())
                           .ticketPrice(request.ticketPrice())
                           .status(EventStatus.UPCOMING)
                           .build();


        int numberOfSeats = request.numberOfSeats();
        List<Seat> seats = new ArrayList<>();

        for (int i = 1; i <= numberOfSeats; i++) {
            Seat seat = Seat.builder()
                            .event(event)
                            .seatNumber(i)
                            .rowName("A")
                            .seatStatus(SeatStatus.AVAILABLE)
                            .build();
            seats.add(seat);
        }

        event.setSeats(seats);

        // Because of CascadeType.ALL, saving the Event will:
        // A. Insert the Event -> Get the ID (e.g., 501)
        // B. Update all the Seats with event_id = 501
        // C. Insert all the Seats
        Event savedEvent = eventRepository.save(event);

        return EventResponse
                .builder()
                .id(savedEvent.getId())
                .name(savedEvent.getName())
                .description(savedEvent.getDescription())
                .venueName(savedEvent.getVenue().getName())
                .ticketPrice(savedEvent.getTicketPrice())
                .status(savedEvent.getStatus().getDisplayName())
                .availableSeats(numberOfSeats)
                .build();
    }

    @Transactional(readOnly = true)
    public EventResponse getEventById(Long id) {
        Event event = eventRepository.findById(id)
                                     .orElseThrow(
                                             () -> new EntityNotFoundException("Event not found")
                                     );

        int availableSeats = (int) seatRepository.countByEventIdAndSeatStatus(event.getId(), SeatStatus.AVAILABLE);

        return buildEventResponse(availableSeats, event);
    }

    @Transactional(readOnly = true)
    public Page<EventResponse> getAllUpcomingEvents(String eventStatus, Pageable pageable) {
        EventStatus status = EnumUtils.toEnum(EventStatus.class, eventStatus);

        Page<Event> events = eventRepository.findEventsByStatus(status, pageable);

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
        Page<Event> events = eventRepository.findByEventDateTimeBetween(request.startDate(), request.endDate(),
                                                                        pageable
        );

        return buildEventResponsePage(events);
    }

    @Transactional
    public EventResponse updateEventById(Long id, CreateEventRequest request) {
        Event event = eventRepository.findById(id)
                                     .orElseThrow(
                                             () -> new EntityNotFoundException("Event not found")
                                     );

        event.setName(request.name());
        event.setDescription(request.description());
        event.setTicketPrice(request.ticketPrice());
        event.setEventDateTime(request.eventDateTime());

        if (!event.getVenue().getId().equals(request.venueId())) {
            Venue newVenue = venueRepository.findById(request.venueId())
                                            .orElseThrow(
                                                    () -> new EntityNotFoundException("Venue not found")
                                            );
            event.setVenue(newVenue);
        }

        if (event.getSeats().size() != request.numberOfSeats()) {
            throw new IllegalArgumentException(
                    "Cannot change seat capacity via update. Please contact support to resize an event."
            );
        }

        Event savedEvent = eventRepository.save(event);

        int availableSeats = (int) seatRepository.countByEventIdAndSeatStatus(
                event.getId(),
                SeatStatus.AVAILABLE
        );

        return buildEventResponse(availableSeats, savedEvent);
    }

    @Transactional
    public void deleteEventById(Long id) {
        Event event = eventRepository.findById(id)
                                     .orElseThrow(() -> new EntityNotFoundException("Event not found"));

        //We don't want to delete the event if people have already booked for the event
        //Instead we'll need to cancel and refund people back, but this logic won't handle that
        boolean hasBookings = event.getBookings() != null && !event.getBookings().isEmpty();
        if (hasBookings) {
            throw new IllegalStateException("Cannot delete event with active bookings. Cancel the event instead.");
        }

        // Will cascade to Seats automatically
        eventRepository.delete(event);
    }


    private EventResponse buildEventResponse(Integer numberOfAvailableSeats, Event event) {
        return EventResponse
                .builder()
                .id(event.getId())
                .name(event.getName())
                .description(event.getDescription())
                .venueName(event.getVenue().getName())
                .ticketPrice(event.getTicketPrice())
                .status(event.getStatus().getDisplayName())
                .availableSeats(numberOfAvailableSeats)
                .build();
    }

    private Page<EventResponse> buildEventResponsePage(Page<Event> eventsPage) {
        return eventsPage.map(
                event -> buildEventResponse(null, event)
        );
    }
}
