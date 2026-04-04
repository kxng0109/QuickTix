package io.github.kxng0109.quicktix.service;

import io.github.kxng0109.quicktix.dto.request.HoldSeatsRequest;
import io.github.kxng0109.quicktix.dto.response.SeatResponse;
import io.github.kxng0109.quicktix.entity.Event;
import io.github.kxng0109.quicktix.entity.Seat;
import io.github.kxng0109.quicktix.entity.User;
import io.github.kxng0109.quicktix.enums.BookingStatus;
import io.github.kxng0109.quicktix.enums.SeatStatus;
import io.github.kxng0109.quicktix.exception.InvalidOperationException;
import io.github.kxng0109.quicktix.repositories.EventRepository;
import io.github.kxng0109.quicktix.repositories.SeatRepository;
import io.github.kxng0109.quicktix.repositories.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatService {

    private final SeatRepository seatRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final SeatLockService seatLockService;

    @Transactional(readOnly = true)
    @Cacheable(value = "availableSeats", key = "#eventId + '-' + #pageable.pageNumber", sync = true)
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
    @CacheEvict(value = "availableSeats", allEntries = true)
    public List<SeatResponse> holdSeats(HoldSeatsRequest request, User currentUser) {
        //Hitting Redis before the actual database is better. It also helps with the connection to the database.
        //Instead of having 10000 users hitting the database at a go, hitting redis by locking seats in redis
        //Will help reduce the number of people that will eventually hit the actual database

        //The purpose of sorting it as seen here to prevent the issue of someone holding seat 101 and 102,
        //and someone else holding seat 102 and 101, at the same time, you might end up with a deadlock and the seat will be unavailable for a while
        List<Long> sortedSeatIds = request.seatIds()
                .stream()
                .sorted()
                .toList();

        List<Long> successfullyLockedSeats = new ArrayList<>();

        try {
            for (Long seatId : sortedSeatIds) {
                boolean lockAcquired = seatLockService.acquireLock(seatId, currentUser.getEmail());
                if (lockAcquired) {
                    successfullyLockedSeats.add(seatId);
                } else {
                    //If one of them fails to be locked, throw an exception so it can all seats locked
                    // can be rollback
                    throw new InvalidOperationException(
                            "One or more requested seats are no longer available. Please select a different group.");
                }
            }

            //First of all, we don't need to lock the database anymore since we are using redis to perform the fast in-memory
            //pessimistic locking. Now, instead of using something like findById, we are using getReferenceById which
            //doesn't make any calls to the database.
            // The reason why is that the currentUser is already the authenticated user from
            //the jwt, which is not easy for someone to crack. Also, if the user doesn't exist later when saving it to the database,
            //an error will be thrown and the seats will end up being rollback.
            //getReferenceById just creates a dummy Java User object that holds the id we gave it.
            //There are times you'll use one over the other. Basically, if you need to know extra details of that id,
            //then use findById, if you just need a pointer to something that you know exists, then getReferenceById
            User user = userRepository.getReferenceById(currentUser.getId());

            List<Seat> seats = seatRepository.findAllById(sortedSeatIds);

            if (seats.size() != sortedSeatIds.size()) {
                throw new EntityNotFoundException("One or more seat IDs are invalid");
            }

            for (Seat seat : seats) {
                validateSeatBelongsToEvent(request.eventId(), seat);

                if (!seat.getSeatStatus().equals(SeatStatus.AVAILABLE)) {
                    boolean isHeldByCurrentUser = seat.getHeldByUser() != null && seat.getHeldByUser().getId()
                                                                                      .equals(user.getId());

                    if (!isHeldByCurrentUser) {
                        throw new IllegalArgumentException("Seat is not available");
                    }
                }

                seat.setHeldAt(Instant.now());
                seat.setHeldByUser(user);
                log.debug("Seat held by user with email: {}", currentUser.getEmail());
                seat.setSeatStatus(SeatStatus.HELD);
            }

            List<Seat> savedSeats = seatRepository.saveAll(seats);
            return savedSeats.stream().map(this::buildSeatResponse).toList();
        }catch (Exception e){
            //If something goes wrong like a seat being locked, postgres crashing or maybe a validation failed
            //We need to release all the locked seats
            for(Long lockedSeatId : successfullyLockedSeats){
                seatLockService.releaseLock(lockedSeatId, currentUser.getEmail());
            }

            throw e;
        }
    }

    @Transactional
    @CacheEvict(value = "availableSeats", allEntries = true)
    public void releaseSeats(HoldSeatsRequest request, User currentUser) {
        List<Seat> seats = seatRepository.findAllById(request.seatIds());
        for (Seat seat : seats) {
            validateSeatBelongsToEvent(request.eventId(), seat);

            if (seat.getHeldByUser() == null || !seat.getHeldByUser().getId().equals(currentUser.getId())) {
                throw new IllegalArgumentException("You cannot release a seat you do not hold.");
            }

            seatLockService.releaseLock(seat.getId(), currentUser.getEmail());

            seat.setSeatStatus(SeatStatus.AVAILABLE);
            seat.setHeldAt(null);
            seat.setHeldByUser(null);
        }

        seatRepository.saveAll(seats);
    }

    /**
     * Forcibly resets a batch of seats to an {@link SeatStatus#AVAILABLE} state,
     * clearing all database allocations and destroying any lingering Redis locks.
     * <p>
     * This administrative override is used to un-jam seats that remain in a {@link SeatStatus#HELD}
     * state despite the expiration of their corresponding Redis TTL. This method triggers a cache eviction
     * for the "availableSeats" cache to ensure frontend clients immediately see the freed capacity.
     * </p>
     *
     * @param seatIds A list of unique seat identifiers to forcefully reset.
     */
    @CacheEvict(value = "availableSeats", allEntries = true)
    public void releaseSeats(List<Long> seatIds){
        try{
            List<Seat> seats = seatRepository.findAllById(seatIds);
            for(Seat seat : seats){
                seatLockService.forceReleaseLock(seat.getId());

                seat.setSeatStatus(SeatStatus.AVAILABLE);
                seat.getBooking().setStatus(BookingStatus.CANCELLED);
                seat.setBooking(null);
                seat.setHeldAt(null);
                seat.setHeldByUser(null);
            }

            seatRepository.saveAll(seats);
        } catch (Exception e) {
            log.error("An issue occurred when trying to release seats: {}.", e.getMessage(), e);
            throw e;
        }
    }

    @Transactional
    @CacheEvict(value = "availableSeats", allEntries = true)
    public void releaseExpiredHolds(Instant cutoffTime) {
        List<Seat> expiredSeats = seatRepository.findBySeatStatusAndHeldAtBefore(
                SeatStatus.HELD,
                cutoffTime
        );

        if (expiredSeats.isEmpty()) return;

        for (Seat seat : expiredSeats) {
            String heldByEmail = seat.getHeldByUser().getEmail();

            seatLockService.releaseLock(seat.getId(), heldByEmail);

            seat.setHeldAt(null);
            seat.setHeldByUser(null);
            seat.setSeatStatus(SeatStatus.AVAILABLE);
        }

        seatRepository.saveAll(expiredSeats);
    }

    public List<Seat> validateAndGetHeldSeats(List<Long> seatIds, Long userId, Long eventId) {
        List<Seat> seats = seatRepository.findAllById(seatIds);

        if (seats.size() != seatIds.size()) {
            throw new EntityNotFoundException("One or more seats not found");
        }

        for (Seat seat : seats) {
            validateSeatBelongsToEvent(eventId, seat);

            //The seat must be held by the user.
            // The user would have selected the seats they wanted before
            // So we need to be sure that it
            boolean isHeldByMe = seat.getSeatStatus() == SeatStatus.HELD
                    && seat.getHeldByUser() != null
                    && seat.getHeldByUser().getId().equals(userId);

            if (!isHeldByMe) {
                throw new IllegalStateException(
                        "Seat " + seat.getSeatNumber() + " is not currently held by you. Please hold the seat first.");
            }
        }

        return seats;
    }


    private void validateSeatBelongsToEvent(Long eventId, Seat seat) {
        if (!seat.getEvent().getId().equals(eventId)) {
            throw new IllegalArgumentException(
                    "Seat " + seat.getId() + " does not belong to Event " + eventId
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
