package io.github.kxng0109.quicktix.service;

import io.github.kxng0109.quicktix.dto.request.HoldSeatsRequest;
import io.github.kxng0109.quicktix.dto.response.SeatResponse;
import io.github.kxng0109.quicktix.entity.Event;
import io.github.kxng0109.quicktix.entity.Seat;
import io.github.kxng0109.quicktix.entity.User;
import io.github.kxng0109.quicktix.enums.SeatStatus;
import io.github.kxng0109.quicktix.repositories.EventRepository;
import io.github.kxng0109.quicktix.repositories.SeatRepository;
import io.github.kxng0109.quicktix.repositories.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SeatService {

    private final SeatRepository seatRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Page<SeatResponse> getAvailableSeats(Long eventId, Pageable pageable) {
        Event event = eventRepository.findById(eventId)
                                     .orElseThrow(
                                             () -> new EntityNotFoundException("Event not found")
                                     );

        Page<Seat> pagedSeats = seatRepository.findByEventIdAndSeatStatus(
                event.getId(),
                SeatStatus.AVAILABLE,
                pageable
        );

        return pagedSeats.map(this::buildSeatResponse);
    }

    @Transactional(readOnly = true)
    public Page<SeatResponse> getAllSeatsByEvent(Long eventId, Pageable pageable) {
        Event event = eventRepository.findById(eventId)
                                     .orElseThrow(
                                             () -> new EntityNotFoundException("Event not found")
                                     );

        Page<Seat> pagedSeats = seatRepository.findByEventId(event.getId(), pageable);

        return pagedSeats.map(this::buildSeatResponse);
    }

    @Transactional
    public void holdSeats(HoldSeatsRequest request) {
        User user = userRepository.findById(request.userId())
                                  .orElseThrow(
                                          () -> new EntityNotFoundException("User not found")
                                  );

        eventRepository.findById(request.eventId())
                       .orElseThrow(
                               () -> new EntityNotFoundException("Event not found")
                       );

        List<Seat> seats = seatRepository.findAllById(request.seatIds());

        if (seats.size() != request.seatIds().size()) {
            throw new EntityNotFoundException("One or more seat IDs are invalid");
        }

        for (Seat seat : seats) {
            validateSeatBelongsToEvent(request, seat);

            if (!seat.getSeatStatus().equals(SeatStatus.AVAILABLE)) {
                boolean isHeldByCurrentUser = seat.getHeldByUser() != null && seat.getHeldByUser().getId()
                                                                                  .equals(user.getId());

                if (!isHeldByCurrentUser) {
                    throw new IllegalArgumentException("Seat is not available");
                }
            }

            seat.setHeldAt(Instant.now());
            seat.setHeldByUser(user);
            seat.setSeatStatus(SeatStatus.HELD);
        }

        seatRepository.saveAll(seats);
    }

    @Transactional
    public void releaseSeats(HoldSeatsRequest request) {
        userRepository.findById(request.userId())
                      .orElseThrow(
                              () -> new EntityNotFoundException("User not found")
                      );

        eventRepository.findById(request.eventId())
                       .orElseThrow(
                               () -> new EntityNotFoundException("Event not found")
                       );

        List<Seat> seats = seatRepository.findAllById(request.seatIds());
        for (Seat seat : seats) {
            validateSeatBelongsToEvent(request, seat);

            if (seat.getHeldByUser() == null || !seat.getHeldByUser().getId().equals(request.userId())) {
                throw new IllegalArgumentException("You cannot release a seat you do not hold.");
            }

            seat.setSeatStatus(SeatStatus.AVAILABLE);
            seat.setHeldAt(null);
            seat.setHeldByUser(null);
        }

        seatRepository.saveAll(seats);
    }

    @Transactional
    public void releaseExpiredHolds(Instant cutoffTime) {
        List<Seat> expiredSeats = seatRepository.findBySeatStatusAndHeldAtBefore(
                SeatStatus.HELD,
                cutoffTime
        );

        if (expiredSeats.isEmpty()) return;

        for (Seat seat : expiredSeats) {
            seat.setHeldAt(null);
            seat.setHeldByUser(null);
            seat.setSeatStatus(SeatStatus.AVAILABLE);
        }

        seatRepository.saveAll(expiredSeats);
    }


    private void validateSeatBelongsToEvent(HoldSeatsRequest request, Seat seat) {
        if (!seat.getEvent().getId().equals(request.eventId())) {
            throw new IllegalArgumentException(
                    "Seat " + seat.getId() + " does not belong to Event " + request.eventId()
            );
        }
    }

    private SeatResponse buildSeatResponse(Seat seat) {
        return SeatResponse.builder()
                           .id(seat.getId())
                           .seatNumber(seat.getSeatNumber())
                           .rowName(seat.getRowName())
                           .status(seat.getSeatStatus().getDisplayName())
                           .build();
    }
}
