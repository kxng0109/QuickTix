package io.github.kxng0109.quicktix.service;

import io.github.kxng0109.quicktix.dto.request.HoldSeatsRequest;
import io.github.kxng0109.quicktix.dto.response.PagedResponse;
import io.github.kxng0109.quicktix.dto.response.SeatResponse;
import io.github.kxng0109.quicktix.entity.Event;
import io.github.kxng0109.quicktix.entity.Seat;
import io.github.kxng0109.quicktix.entity.User;
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

	/**
	 * Retrieves a paginated list of available seats for a specific event.
	 * <p>
	 * This method is transactional and read-only, ensuring data consistency without modifying the database state.
	 * It leverages Redis caching with synchronous updates to improve performance by avoiding repeated database queries
	 * when multiple requests are made for the same event and page number.
	 * </p>
	 * <p>
	 * The cache key combines the event ID and page number, ensuring that only relevant cache entries are invalidated
	 * when seats become unavailable (e.g., during seat holding operations).
	 * </p>
	 *
	 * @param eventId  The unique identifier of the event for which to retrieve available seats.
	 *                 If not found, an {@link EntityNotFoundException} will be thrown.
	 * @param pageable Pagination metadata including page number and size.
	 *                 Use {@code PageRequest.of(pageNumber, pageSize)} to create a valid instance.
	 * @return A paginated list of seat responses containing available seats for the specified event.
	 * Each response includes the seat ID, seat number, row name, and status display name.
	 * @throws EntityNotFoundException if the event with the given ID does not exist in the database.
	 */
	@Transactional(readOnly = true)
	@Cacheable(
			value = "availableSeats",
			key = "#eventId + '-' + #pageable.pageNumber + '-' + #pageable.pageSize",
			sync = true
	)
	public PagedResponse<SeatResponse> getAvailableSeats(Long eventId, Pageable pageable) {
		Event event = eventRepository.findById(eventId)
		                             .orElseThrow(
				                             () -> new EntityNotFoundException("Event not found")
		                             );

		Page<Seat> pagedSeats = seatRepository.findByEventIdAndSeatStatus(
				event.getId(),
				SeatStatus.AVAILABLE,
				pageable
		);

		Page<SeatResponse> seatResponsePage = pagedSeats.map(this::buildSeatResponse);
		return PagedResponse.from(seatResponsePage);
	}

	/**
	 * Retrieves a paginated list of all seats (regardless of status) for a specific event.
	 *
	 * @param eventId  The unique identifier of the event.
	 * @param pageable Pagination metadata.
	 * @return A paginated list of all seats associated with the event.
	 * @throws EntityNotFoundException if the event does not exist.
	 */
	@Transactional(readOnly = true)
	public Page<SeatResponse> getAllSeatsByEvent(Long eventId, Pageable pageable) {
		Event event = eventRepository.findById(eventId)
		                             .orElseThrow(
				                             () -> new EntityNotFoundException("Event not found")
		                             );

		Page<Seat> pagedSeats = seatRepository.findByEventId(event.getId(), pageable);

		return pagedSeats.map(this::buildSeatResponse);
	}

	/**
	 * Temporarily holds a batch of seats for a user to prevent race conditions during checkout.
	 * <p>
	 * This method utilizes pessimistic locking via Redis and the database. It first attempts to acquire an atomic lock
	 * for every requested seat. If successful, it verifies the database state and updates the seats
	 * to {@link SeatStatus#HELD}. If any lock or validation fails, it safely rolls back and releases
	 * any partially acquired Redis locks to prevent deadlocks.
	 * </p>
	 *
	 * @param request     The payload containing the event ID and the list of seat IDs to hold.
	 * @param currentUser The authenticated user attempting to hold the seats.
	 * @return A list of successfully held {@link SeatResponse} objects.
	 * @throws InvalidOperationException if one or more seats are already locked in Redis.
	 * @throws IllegalArgumentException  if a seat is not in an AVAILABLE state in the database.
	 */
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

			List<Seat> seats = seatRepository.findAllByIdWithLock(sortedSeatIds);

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
		} catch (Exception e) {
			//If something goes wrong like a seat being locked, postgres crashing or maybe a validation failed
			//We need to release all the locked seats
			for (Long lockedSeatId : successfullyLockedSeats) {
				seatLockService.releaseLock(lockedSeatId, currentUser.getEmail());
			}

			throw e;
		}
	}

	/**
	 * Allows a user to intentionally release seats they are currently holding.
	 * <p>
	 * Validates that the current user actually owns the hold before releasing the Redis lock
	 * and resetting the database state to {@link SeatStatus#AVAILABLE}.
	 * </p>
	 *
	 * @param request     The payload containing the seat IDs to release.
	 * @param currentUser The authenticated user requesting the release.
	 * @throws IllegalArgumentException if the user attempts to release a seat held by someone else.
	 */
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
	public void releaseSeats(List<Long> seatIds) {
		try {
			List<Seat> seats = seatRepository.findAllById(seatIds);
			for (Seat seat : seats) {
				seatLockService.forceReleaseLock(seat.getId());

				seat.setSeatStatus(SeatStatus.AVAILABLE);
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

	/**
	 * System task invoked by the Scheduler to release seat holds that have timed out.
	 *
	 * @param cutoffTime The timestamp defining the expiration threshold (e.g., 15 minutes ago).
	 */
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
			if(heldByEmail == null){
				log.warn("Seat with ID {} has a null email field. Can not release Redis lock!", seat.getId());
			}else{
				seatLockService.releaseLock(seat.getId(), heldByEmail);
			}

			seat.setHeldAt(null);
			seat.setHeldByUser(null);
			seat.setSeatStatus(SeatStatus.AVAILABLE);
		}

		seatRepository.saveAll(expiredSeats);
	}

	/**
	 * INTERNAL USE ONLY.
	 * Validates that a requested batch of seats is currently held by the specified user
	 * before allowing a pending booking to be generated.
	 *
	 * @param seatIds The list of seat IDs attempting to be booked.
	 * @param userId  The ID of the user attempting to book them.
	 * @param eventId The ID of the event the seats belong to.
	 * @return The validated list of {@link Seat} entities.
	 * @throws IllegalStateException if any seat is not currently held by the specified user.
	 */
	@Transactional(readOnly = true)
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
