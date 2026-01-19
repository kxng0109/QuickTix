package io.github.kxng0109.quicktix.service;

import io.github.kxng0109.quicktix.dto.request.HoldSeatsRequest;
import io.github.kxng0109.quicktix.dto.response.SeatResponse;
import io.github.kxng0109.quicktix.entity.Event;
import io.github.kxng0109.quicktix.entity.Seat;
import io.github.kxng0109.quicktix.entity.User;
import io.github.kxng0109.quicktix.entity.Venue;
import io.github.kxng0109.quicktix.enums.EventStatus;
import io.github.kxng0109.quicktix.enums.SeatStatus;
import io.github.kxng0109.quicktix.repositories.EventRepository;
import io.github.kxng0109.quicktix.repositories.SeatRepository;
import io.github.kxng0109.quicktix.repositories.UserRepository;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SeatServiceTest {

    private final long eventId = 100L;
    private final long userId = 200L;
    private final List<Long> seatIds = List.of(300L, 301L, 302L);
    private final int availableSeats = seatIds.size();
    private final Pageable pageable = PageRequest.of(0, availableSeats);

    @Mock
    private SeatRepository seatRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SeatService seatService;

    private List<Seat> seats = new ArrayList<>();
    private Event event;
    private User user;
    private HoldSeatsRequest holdSeatsRequest;

    @BeforeEach
    void setUp() {
        event = Event.builder()
                     .id(eventId)
                     .name("An event")
                     .description("Event description")
                     .seats(seats)
                     .venue(new Venue())
                     .status(EventStatus.UPCOMING)
                     .eventDateTime(Instant.now().plus(1, ChronoUnit.HOURS))
                     .ticketPrice(BigDecimal.valueOf(1395.65))
                     .build();


        for (int i = 0; i < availableSeats; i++) {
            Seat seat = Seat.builder()
                            .id(seatIds.get(i))
                            .seatStatus(SeatStatus.AVAILABLE)
                            .event(event)
                            .build();

            seats.add(seat);
        }

        user = User.builder()
                   .id(userId)
                   .firstName("Adam")
                   .lastName("Baker")
                   .email("adam@baker.com")
                   .phoneNumber("054685563")
                   .build();

        holdSeatsRequest = HoldSeatsRequest.builder()
                                           .eventId(eventId)
                                           .userId(userId)
                                           .seatIds(seatIds)
                                           .build();
    }

    @Test
    public void getAvailableSeats_should_returnAPageOfAvailableSeats_whenAllCorrect() {
        Page<Seat> seatPage = new PageImpl<>(seats);

        when(eventRepository.findById(anyLong()))
                .thenReturn(Optional.of(event));
        when(seatRepository.findByEventIdAndSeatStatus(
                     anyLong(),
                     eq(SeatStatus.AVAILABLE),
                     any(Pageable.class)
             )
        ).thenReturn(seatPage);

        Page<SeatResponse> responses = seatService.getAvailableSeats(
                eventId,
                pageable
        );

        assertNotNull(responses);
        assertEquals(availableSeats, responses.getTotalElements());

        verify(eventRepository).findById(anyLong());
        verify(seatRepository).findByEventIdAndSeatStatus(anyLong(), eq(SeatStatus.AVAILABLE), any(Pageable.class));
    }

    @Test
    public void getAvailableSeats_should_throwEntityNotFoundException_whenNoEventIsFound() {
        when(eventRepository.findById(anyLong()))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> seatService.getAvailableSeats(eventId, pageable)
        );

        verify(eventRepository).findById(anyLong());
        verify(seatRepository, never()).findByEventIdAndSeatStatus(anyLong(), eq(SeatStatus.AVAILABLE),
                                                                   any(Pageable.class)
        );
    }

    @Test
    public void getAllSeatsByEvent_should_returnAPageOfAvailableAllSeats_whenAllCorrect() {
        Page<Seat> seatPage = new PageImpl<>(seats);

        when(eventRepository.findById(anyLong()))
                .thenReturn(Optional.of(event));
        when(seatRepository.findByEventId(
                     anyLong(),
                     any(Pageable.class)
             )
        ).thenReturn(seatPage);

        Page<SeatResponse> responses = seatService.getAllSeatsByEvent(
                eventId,
                pageable
        );

        assertNotNull(responses);
        assertEquals(availableSeats, responses.getTotalElements());

        verify(eventRepository).findById(anyLong());
        verify(seatRepository).findByEventId(anyLong(), any(Pageable.class));
    }

    @Test
    public void getAllSeatsByEvent_should_throwEntityNotFoundException_whenNoEventIsFound() {
        when(eventRepository.findById(anyLong()))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> seatService.getAllSeatsByEvent(eventId, pageable)
        );

        verify(eventRepository).findById(anyLong());
        verify(seatRepository, never()).findByEventId(anyLong(), any(Pageable.class));
    }

    @Test
    public void holdSeats_should_holdSeatsAndReturnNothing_whenAllCorrect() {
        when(userRepository.findById(anyLong()))
                .thenReturn(Optional.of(user));
        when(eventRepository.findById(anyLong()))
                .thenReturn(Optional.of(event));
        when(seatRepository.findAllById(eq(seatIds)))
                .thenReturn(seats);

        seatService.holdSeats(holdSeatsRequest);

        verify(userRepository).findById(anyLong());
        verify(eventRepository).findById(anyLong());
        verify(seatRepository).findAllById(eq(seatIds));
        verify(seatRepository).saveAll(seats);
    }

    @Test
    public void holdSeats_should_throwEntityNotFoundException_whenNoUserIsFound() {
        when(userRepository.findById(anyLong()))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> seatService.holdSeats(holdSeatsRequest)
        );

        verify(userRepository).findById(anyLong());
        verify(eventRepository, never()).findById(anyLong());
        verify(seatRepository, never()).findByEventId(anyLong(), any(Pageable.class));
        verify(seatRepository, never()).saveAll(seats);
    }

    @Test
    public void holdSeats_should_throwEntityNotFoundException_whenNoEventIsFound() {
        when(userRepository.findById(anyLong()))
                .thenReturn(Optional.of(user));
        when(eventRepository.findById(anyLong()))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> seatService.holdSeats(holdSeatsRequest)
        );

        verify(userRepository).findById(anyLong());
        verify(eventRepository).findById(anyLong());
        verify(seatRepository, never()).findByEventId(anyLong(), any(Pageable.class));
        verify(seatRepository, never()).saveAll(seats);
    }

    @Test
    public void holdSeats_should_throwEntityNotFoundException_whenSeatSizesAreDifferent() {
        seats = List.of();

        when(userRepository.findById(anyLong()))
                .thenReturn(Optional.of(user));
        when(eventRepository.findById(anyLong()))
                .thenReturn(Optional.of(event));
        when(seatRepository.findAllById(eq(seatIds)))
                .thenReturn(seats);

        assertThrows(
                EntityNotFoundException.class,
                () -> seatService.holdSeats(holdSeatsRequest)
        );

        verify(userRepository).findById(anyLong());
        verify(eventRepository).findById(anyLong());
        verify(seatRepository).findAllById(eq(seatIds));
        verify(seatRepository, never()).saveAll(seats);
    }

    @Test
    public void holdSeats_should_throwIllegalArgumentException_whenSeatDoesntBelongToEvent() {
        holdSeatsRequest = HoldSeatsRequest.builder()
                                           .seatIds(seatIds)
                                           .eventId(eventId + 1L)
                                           .userId(userId)
                                           .build();

        when(userRepository.findById(anyLong()))
                .thenReturn(Optional.of(user));
        when(eventRepository.findById(anyLong()))
                .thenReturn(Optional.of(event));
        when(seatRepository.findAllById(eq(seatIds)))
                .thenReturn(seats);

        assertThrows(
                IllegalArgumentException.class,
                () -> seatService.holdSeats(holdSeatsRequest)
        );

        verify(userRepository).findById(anyLong());
        verify(eventRepository).findById(anyLong());
        verify(seatRepository).findAllById(eq(seatIds));
        verify(seatRepository, never()).saveAll(seats);
    }

    @Test
    public void holdSeats_should_throwIllegalArgumentException_whenSeatIsNotAvailable() {
        seats.forEach(seat -> seat.setSeatStatus(SeatStatus.HELD));

        when(userRepository.findById(anyLong()))
                .thenReturn(Optional.of(user));
        when(eventRepository.findById(anyLong()))
                .thenReturn(Optional.of(event));
        when(seatRepository.findAllById(eq(seatIds)))
                .thenReturn(seats);

        assertThrows(
                IllegalArgumentException.class,
                () -> seatService.holdSeats(holdSeatsRequest)
        );

        verify(userRepository).findById(anyLong());
        verify(eventRepository).findById(anyLong());
        verify(seatRepository).findAllById(eq(seatIds));
        verify(seatRepository, never()).saveAll(seats);
    }

    @Test
    public void releaseSeats_should_releaseSeatsAndReturnNothing_whenSeatBelongsToTheUser() {
        seats.forEach(seat -> seat.setHeldByUser(user));

        when(userRepository.findById(anyLong()))
                .thenReturn(Optional.of(user));
        when(eventRepository.findById(anyLong()))
                .thenReturn(Optional.of(event));
        when(seatRepository.findAllById(eq(seatIds)))
                .thenReturn(seats);

        seatService.releaseSeats(holdSeatsRequest);

        verify(eventRepository).findById(anyLong());
        verify(seatRepository).findAllById(eq(seatIds));
        verify(seatRepository).saveAll(seats);
    }

    @Test
    public void releaseSeats_should_throwEntityNotFoundException_whenNoUserIsFound() {
        when(userRepository.findById(anyLong()))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> seatService.releaseSeats(holdSeatsRequest)
        );

        verify(userRepository).findById(anyLong());
        verify(eventRepository, never()).findById(anyLong());
        verify(seatRepository, never()).findByEventId(anyLong(), any(Pageable.class));
        verify(seatRepository, never()).saveAll(seats);
    }

    @Test
    public void releaseSeats_should_throwEntityNotFoundException_whenNoEventIsFound() {
        when(userRepository.findById(anyLong()))
                .thenReturn(Optional.of(user));
        when(eventRepository.findById(anyLong()))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> seatService.releaseSeats(holdSeatsRequest)
        );

        verify(userRepository).findById(anyLong());
        verify(eventRepository).findById(anyLong());
        verify(seatRepository, never()).findByEventId(anyLong(), any(Pageable.class));
        verify(seatRepository, never()).saveAll(seats);
    }

    @Test
    public void releaseSeats_should_throwIllegalArgumentException_whenSeatIsNotAvailable() {
        seats.forEach(seat -> seat.setHeldByUser(null));

        when(userRepository.findById(anyLong()))
                .thenReturn(Optional.of(user));
        when(eventRepository.findById(anyLong()))
                .thenReturn(Optional.of(event));
        when(seatRepository.findAllById(eq(seatIds)))
                .thenReturn(seats);

        assertThrows(
                IllegalArgumentException.class,
                () -> seatService.releaseSeats(holdSeatsRequest)
        );

        verify(userRepository).findById(anyLong());
        verify(eventRepository).findById(anyLong());
        verify(seatRepository).findAllById(eq(seatIds));
        verify(seatRepository, never()).saveAll(seats);
    }

    @Test
    public void releaseExpiredHolds_should_setHeldSeatsToAvailable() {
        Instant cutoff = Instant.now().minus(15, ChronoUnit.MINUTES);

        seats.forEach(seat -> {
            seat.setSeatStatus(SeatStatus.HELD);
            seat.setHeldAt(cutoff.minus(1, ChronoUnit.MINUTES));
        });

        when(seatRepository.findBySeatStatusAndHeldAtBefore(eq(SeatStatus.HELD), any(Instant.class)))
                .thenReturn(seats);

        seatService.releaseExpiredHolds(cutoff);

        assertEquals(SeatStatus.AVAILABLE, seats.getFirst().getSeatStatus());
        assertNull(seats.getFirst().getHeldByUser());

        verify(seatRepository).findBySeatStatusAndHeldAtBefore(eq(SeatStatus.HELD), any(Instant.class));
        verify(seatRepository).saveAll(seats);
    }
}
