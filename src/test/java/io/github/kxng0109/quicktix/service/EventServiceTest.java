package io.github.kxng0109.quicktix.service;

import io.github.kxng0109.quicktix.dto.request.CreateEventRequest;
import io.github.kxng0109.quicktix.dto.request.EventDateSearchRequest;
import io.github.kxng0109.quicktix.dto.response.EventResponse;
import io.github.kxng0109.quicktix.entity.Booking;
import io.github.kxng0109.quicktix.entity.Event;
import io.github.kxng0109.quicktix.entity.Seat;
import io.github.kxng0109.quicktix.entity.Venue;
import io.github.kxng0109.quicktix.enums.BookingStatus;
import io.github.kxng0109.quicktix.enums.EventStatus;
import io.github.kxng0109.quicktix.enums.SeatStatus;
import io.github.kxng0109.quicktix.repositories.EventRepository;
import io.github.kxng0109.quicktix.repositories.SeatRepository;
import io.github.kxng0109.quicktix.repositories.VenueRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class EventServiceTest {

    private final Long venueId = 500L;
    private final Long eventId = 100L;

    private final List<Seat> seats = new ArrayList<>();
    private final int numberOfSeats = 3;
    private final Pageable pageable = PageRequest.of(0, 10);

    @Mock
    private EventRepository eventRepository;
    @Mock
    private VenueRepository venueRepository;
    @Mock
    private SeatRepository seatRepository;

    @InjectMocks
    private EventService eventService;

    private CreateEventRequest request;
    private Event event;
    private Venue venue;

    @BeforeEach
    public void setUp() {
        request = CreateEventRequest.builder()
                                    .name("Lorem Ipsum event")
                                    .description(
                                            "An event that has lorem ipsum text every where and everyone talks in lorem ipsum")
                                    .venueId(venueId)
                                    .eventDateTime(Instant.now())
                                    .ticketPrice(BigDecimal.valueOf(13455.99))
                                    .numberOfSeats(numberOfSeats)
                                    .build();

        venue = Venue.builder()
                     .id(venueId)
                     .build();


        event = Event.builder()
                     .id(eventId)
                     .name(request.name())
                     .description(request.description())
                     .venue(venue)
                     .status(EventStatus.UPCOMING)
                     .seats(seats)
                     .build();

        for (int i = 1; i <= numberOfSeats; i++) {
            Seat seat = Seat.builder()
                            .event(event)
                            .seatStatus(SeatStatus.AVAILABLE)
                            .id((long) i)
                            .build();
            seats.add(seat);
        }

        event.setSeats(seats);
    }

    @Test
    public void createEvent_should_returnEventResponse_whenRequestIsValid() {
        when(venueRepository.findById(venueId))
                .thenReturn(Optional.of(venue));
        when(eventRepository.save(any(Event.class)))
                .thenAnswer(i -> i.getArgument(0));

        EventResponse response = eventService.createEvent(request);

        assertNotNull(response);

        verify(venueRepository).findById(venueId);
        verify(eventRepository).save(any(Event.class));
    }

    @Test
    public void createEvent_should_throwEntityNotFoundException_whenVenueDoesNotExist() {
        when(venueRepository.findById(venueId))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> eventService.createEvent(request)
        );

        verify(venueRepository).findById(venueId);
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    public void getEventById_should_returnEventResponse_whenRequestIsValid() {
        when(eventRepository.findById(eventId))
                .thenReturn(Optional.of(event));

        when(seatRepository.countByEventIdAndSeatStatus(eventId, SeatStatus.AVAILABLE))
                .thenReturn((long) seats.size());

        EventResponse response = eventService.getEventById(eventId);

        assertNotNull(response);
        assertEquals(seats.size(), response.availableSeats());

        verify(eventRepository).findById(eventId);
        verify(seatRepository).countByEventIdAndSeatStatus(eventId, SeatStatus.AVAILABLE);
    }

    @Test
    public void getEventById_should_throwEntityNotFoundException_whenEventDoesNotExist() {
        when(eventRepository.findById(eventId))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> eventService.getEventById(eventId)
        );

        verify(eventRepository).findById(eventId);
        verify(seatRepository, never()).countByEventIdAndSeatStatus(anyLong(), any(SeatStatus.class));
    }

    @Test
    public void getAllUpcomingEvents_should_returnListOfEventResponse_whenRequestIsValid() {
        Page<Event> eventPage = new PageImpl<>(List.of(event));

        when(eventRepository.findEventsByStatus(any(EventStatus.class), any(Pageable.class)))
                .thenReturn(eventPage);

        Page<EventResponse> response = eventService.getAllUpcomingEvents("upcoming", pageable);

        assertNotNull(response);
        assertEquals(1, response.getTotalElements());
        assertEquals(event.getDescription(), response.getContent().getFirst().description());

        verify(eventRepository).findEventsByStatus(any(EventStatus.class), any(Pageable.class));
    }

    @Test
    public void getEventsByVenueId_should_returnListOfEventResponse_whenRequestIsValid() {
        Page<Event> eventPage = new PageImpl<>(List.of(event));

        when(venueRepository.findById(anyLong()))
                .thenReturn(Optional.of(venue));
        when(eventRepository.findByVenue(any(Venue.class), any(Pageable.class)))
                .thenReturn(eventPage);

        Page<EventResponse> response = eventService.getEventsByVenueId(venueId, pageable);

        assertNotNull(response);
        assertEquals(1, response.getTotalElements());
        assertEquals(event.getDescription(), response.getContent().getFirst().description());

        verify(venueRepository).findById(anyLong());
        verify(eventRepository).findByVenue(any(Venue.class), any(Pageable.class));
    }

    @Test
    public void getEventsByVenueId_should_throwEntityNotFoundException_whenVenueDoesNotExist() {
        when(venueRepository.findById(anyLong()))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> eventService.getEventsByVenueId(venueId, pageable)
        );

        verify(venueRepository).findById(anyLong());
    }

    @Test
    public void getEventsByDateRange_should_returnListOfEventResponse_whenRequestIsValid() {
        Instant now = Instant.now();
        EventDateSearchRequest searchRequest = new EventDateSearchRequest(
                now.plus(1, ChronoUnit.HOURS),
                now.plus(2, ChronoUnit.DAYS)
        );

        Page<Event> eventPage = new PageImpl<>(List.of(event));

        when(eventRepository.findByEventDateTimeBetween(any(Instant.class), any(Instant.class), any(Pageable.class)))
                .thenReturn(eventPage);

        Page<EventResponse> responses = eventService.getEventsByDateRange(searchRequest, pageable);

        assertNotNull(responses);
        assertEquals(1, responses.getTotalElements());

        verify(eventRepository).findByEventDateTimeBetween(any(Instant.class), any(Instant.class), any(Pageable.class));
    }

    @Test
    public void updateEventById_should_returnUpdatedEventResponse_whenRequestIsValid() {
        when(eventRepository.findById(anyLong()))
                .thenReturn(Optional.of(event));
        when(eventRepository.save(any(Event.class)))
                .thenAnswer(i -> i.getArgument(0));
        when(seatRepository.countByEventIdAndSeatStatus(anyLong(), any(SeatStatus.class)))
                .thenReturn((long) numberOfSeats);

        EventResponse response = eventService.updateEventById(eventId, request);

        assertNotNull(response);
        assertEquals(event.getDescription(), response.description());

        verify(eventRepository).findById(anyLong());
        verify(eventRepository).save(any(Event.class));
        verify(seatRepository).countByEventIdAndSeatStatus(anyLong(), any(SeatStatus.class));
    }

    @Test
    public void updateEventById_should_throwIllegalArgumentException_whenSizeChangeIsInRequest() {
        event.setSeats(List.of());
        when(eventRepository.findById(anyLong()))
                .thenReturn(Optional.of(event));

        assertThrows(
                IllegalArgumentException.class,
                () -> eventService.updateEventById(eventId, request)
        );

        verify(eventRepository).findById(anyLong());
        verify(eventRepository, never()).save(any(Event.class));
        verify(seatRepository, never()).countByEventIdAndSeatStatus(anyLong(), any(SeatStatus.class));
    }

    @Test
    public void updateEventById_should_throwEntityNotFoundException_whenEventDoesNotExist() {
        when(eventRepository.findById(anyLong()))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> eventService.updateEventById(eventId, request)
        );

        verify(eventRepository).findById(anyLong());
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    public void updateEventById_should_throwEntityNotFoundException_whenVenueDoesNotExist() {
        event.setVenue(Venue.builder().id(venueId + 1L).build());

        when(eventRepository.findById(anyLong()))
                .thenReturn(Optional.of(event));
        when(venueRepository.findById(anyLong()))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> eventService.updateEventById(eventId, request)
        );

        verify(eventRepository).findById(anyLong());
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    public void deleteEventById_should_returnNothing_whenRequestIsValid() {
        when(eventRepository.findById(anyLong()))
                .thenReturn(Optional.of(event));

        eventService.deleteEventById(eventId);

        verify(eventRepository).findById(anyLong());
        verify(eventRepository).delete(any(Event.class));

    }

    @Test
    public void deleteEventById_should_throwEntityNotFoundException_whenIdDoesNotExist() {
        when(eventRepository.findById(anyLong()))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> eventService.deleteEventById(eventId)
        );

        verify(eventRepository).findById(anyLong());
        verify(eventRepository, never()).delete(any(Event.class));
    }

    @Test
    public void deleteEventById_should_throwIllegalStateException_whenEventHasBookings() {
        event.setBookings(List.of(
                Booking.builder().event(event).status(BookingStatus.CONFIRMED).build()
        ));

        when(eventRepository.findById(anyLong()))
                .thenReturn(Optional.of(event));

        assertThrows(
                IllegalStateException.class,
                () -> eventService.deleteEventById(eventId)
        );

        verify(eventRepository).findById(anyLong());
        verify(eventRepository, never()).delete(any(Event.class));
    }

}
