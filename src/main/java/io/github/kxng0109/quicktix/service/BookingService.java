package io.github.kxng0109.quicktix.service;

import io.github.kxng0109.quicktix.dto.request.InitiateBookingRequest;
import io.github.kxng0109.quicktix.dto.response.BookingResponse;
import io.github.kxng0109.quicktix.dto.response.SeatResponse;
import io.github.kxng0109.quicktix.entity.*;
import io.github.kxng0109.quicktix.enums.BookingStatus;
import io.github.kxng0109.quicktix.enums.PaymentStatus;
import io.github.kxng0109.quicktix.enums.SeatStatus;
import io.github.kxng0109.quicktix.exception.InvalidOperationException;
import io.github.kxng0109.quicktix.repositories.BookingRepository;
import io.github.kxng0109.quicktix.repositories.EventRepository;
import io.github.kxng0109.quicktix.repositories.SeatRepository;
import io.github.kxng0109.quicktix.repositories.UserRepository;
import io.github.kxng0109.quicktix.utils.AssertOwnershipOrAdmin;
import io.github.kxng0109.quicktix.utils.BookingReferenceGenerator;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Core service managing the entire lifecycle of ticket bookings.
 * <p>
 * This service handles the creation of pending bookings, dynamic price calculations,
 * booking confirmations after payment, and both user-initiated and system-initiated cancellations.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

	private final BookingRepository bookingRepository;
	private final UserRepository userRepository;
	private final EventRepository eventRepository;
	private final SeatRepository seatRepository;
	private final SeatService seatService;
	private final SeatLockService seatLockService;

	/**
	 * Retrieves a specific booking by its internal database ID.
	 * <p>
	 * Enforces strict authorization by verifying through {@link AssertOwnershipOrAdmin} that the requesting
	 * user either legitimately owns the booking or possesses system-wide administrative privileges.
	 * </p>
	 *
	 * @param bookId      The unique internal identifier of the booking.
	 * @param currentUser The currently authenticated user making the request.
	 * @return A {@link BookingResponse} detailing the booking and its associated seats.
	 * @throws EntityNotFoundException if the requested booking ID does not exist.
	 * @throws org.springframework.security.access.AccessDeniedException if the user lacks ownership or admin rights.
	 */
	@Transactional(readOnly = true)
	public BookingResponse getBookingById(Long bookId, User currentUser) {
		Booking booking = bookingRepository.findById(bookId)
		                                   .orElseThrow(
				                                   () -> new EntityNotFoundException("Booking not found.")
		                                   );

		AssertOwnershipOrAdmin.check(currentUser, booking.getUser());

		return buildBookingResponse(booking);
	}

	/**
	 * Retrieves a specific booking using its human-readable public reference code.
	 * <p>
	 * Primarily utilized for customer support lookups or order tracking. It enforces the exact same
	 * strict ownership and authorization constraints as ID-based retrieval.
	 * </p>
	 *
	 * @param bookingReference The unique public alphanumeric reference (e.g., "QT-A9K4P2").
	 * @param currentUser      The currently authenticated user making the request.
	 * @return A {@link BookingResponse} detailing the booking and its associated seats.
	 * @throws EntityNotFoundException if no booking matches the provided reference string.
	 * @throws org.springframework.security.access.AccessDeniedException if the user lacks ownership or admin rights.
	 */
	@Transactional(readOnly = true)
	public BookingResponse getBookingByReference(String bookingReference, User currentUser) {
		Booking booking = bookingRepository.findByBookingReference(bookingReference)
		                                   .orElseThrow(
				                                   () -> new EntityNotFoundException("Booking not found.")
		                                   );

		AssertOwnershipOrAdmin.check(currentUser, booking.getUser());


		return buildBookingResponse(booking);
	}

	/**
	 * Retrieves a paginated history of all bookings associated with a specific user account.
	 * <p>
	 * Verifies that the user attempting to access the list is either the target account owner
	 * or a system administrator, preventing lateral data scraping by standard users.
	 * </p>
	 *
	 * @param userId      The ID of the user whose booking history is being queried.
	 * @param pageable    Pagination metadata (page number, page size, sort parameters).
	 * @param currentUser The currently authenticated user making the request.
	 * @return A paginated {@link Page} of {@link BookingResponse} objects.
	 * @throws EntityNotFoundException if the target user account does not exist.
	 * @throws org.springframework.security.access.AccessDeniedException if the user lacks ownership or admin rights.
	 */
	@Transactional(readOnly = true)
	public Page<BookingResponse> getBookingsByUser(Long userId, Pageable pageable, User currentUser) {
		User user = userRepository.findById(userId)
		                          .orElseThrow(
				                          () -> new EntityNotFoundException("User not found.")
		                          );

		AssertOwnershipOrAdmin.check(currentUser, user);

		Page<Booking> bookings = bookingRepository.findByUserId(user.getId(), pageable);

		return bookings.map(this::buildBookingResponse);
	}

	/**
	 * Initializes a new pending booking for a user.
	 * <p>
	 * <b>Security Note:</b> This method calculates the {@code totalAmount} strictly on the backend
	 * by multiplying the event's ticket price by the number of valid, user-held seats. It completely
	 * ignores any pricing data sent from the frontend to prevent manipulation.
	 * </p>
	 *
	 * @param request     The payload containing the Event ID and requested Seat IDs.
	 * @param currentUser The authenticated user initiating the booking.
	 * @return A {@link BookingResponse} detailing the pending booking and the generated reference code.
	 * @throws EntityNotFoundException if the event or requested seats do not exist.
	 * @throws IllegalStateException   if the requested seats are not currently held by the user in Redis.
	 */
	@Transactional
	public BookingResponse createPendingBooking(InitiateBookingRequest request, User currentUser) {
		Event event = eventRepository.findById(request.eventId())
		                             .orElseThrow(
				                             () -> new EntityNotFoundException("Event not found.")
		                             );

		AssertOwnershipOrAdmin.check(currentUser, currentUser);

		List<Seat> seats = seatService.validateAndGetHeldSeats(
				request.seatIds(),
				currentUser.getId(),
				request.eventId()
		);

		BigDecimal calculatedTotalAmount = event.getTicketPrice()
		                                        .multiply(BigDecimal.valueOf(seats.size()));

		Booking booking = Booking.builder()
		                         .user(currentUser)
		                         .event(event)
		                         .seats(seats)
		                         .status(BookingStatus.PENDING)
		                         .bookingReference(generateUniqueBookingReference())
		                         .totalAmount(calculatedTotalAmount)
		                         .build();

		Booking savedBooking = bookingRepository.save(booking);

		for (Seat seat : seats) {
			seat.setBooking(savedBooking);
		}
		seatRepository.saveAll(seats);

		return buildBookingResponse(savedBooking);
	}

	/**
	 * Confirms a pending booking after a successful payment transaction.
	 * <p>
	 * Transitions the booking state to {@link BookingStatus#CONFIRMED} and permanently
	 * assigns the reserved seats to the user (updating seat status to {@link SeatStatus#BOOKED}).
	 * </p>
	 *
	 * @param bookingId The unique identifier of the booking to confirm.
	 * @throws InvalidOperationException if the booking is not PENDING or if the payment is missing/incomplete.
	 */
	@Transactional
	public void confirmBooking(Long bookingId) {
		Booking booking = bookingRepository.findByIdWithPayment(bookingId)
		                                   .orElseThrow(
				                                   () -> new EntityNotFoundException("Booking not found.")
		                                   );

		//If the booking was already previously confirmed, then don't do it again
		//Just build and return a BookingResponse
		if (booking.getStatus().equals(BookingStatus.CONFIRMED)) return;

		if (!booking.getStatus().equals(BookingStatus.PENDING)) {
			throw new EntityNotFoundException("Cannot confirm a booking that is in status: " + booking.getStatus());
		}

		Payment payment = booking.getPayment();
		if (payment == null || !payment.getStatus().equals(PaymentStatus.COMPLETED)) {
			throw new InvalidOperationException("Cannot confirm a booking. Payment is missing or not completed");
		}

		booking.setStatus(BookingStatus.CONFIRMED);
		for (Seat seat : booking.getSeats()) {
			seat.setSeatStatus(SeatStatus.BOOKED);
		}

		seatRepository.saveAll(booking.getSeats());
		bookingRepository.save(booking);
	}

	/**
	 * Allows a user to manually cancel a pending booking.
	 * <p>
	 * <b>Note:</b> Currently restricts the cancellation of {@code CONFIRMED} bookings. Confirmed
	 * bookings must be cancelled via administrative support to trigger the necessary financial refunds.
	 * </p>
	 *
	 * @param bookingId   The ID of the booking to cancel.
	 * @param currentUser The user attempting the cancellation (checked for ownership).
	 * @throws InvalidOperationException if the booking is already confirmed or cancelled.
	 */
	@Transactional
	public void cancelBooking(Long bookingId, User currentUser) {
		Booking booking = bookingRepository.findById(bookingId)
		                                   .orElseThrow(
				                                   () -> new EntityNotFoundException("Booking not found.")
		                                   );

		AssertOwnershipOrAdmin.check(currentUser, booking.getUser());

		if (booking.getStatus().equals(BookingStatus.CONFIRMED)) {
			//TODO: in the future, I think we'll need to trigger refunds
			throw new InvalidOperationException(
					"Cannot cancel a confirmed booking yet. Please contact support for refunds.");
		}

		if (!booking.getStatus().equals(BookingStatus.PENDING)) {
			throw new InvalidOperationException("Cannot cancel booking. Status is already: " + booking.getStatus());
		}

		handleBookingCancellation(booking);
	}

	/**
	 * INTERNAL USE ONLY.
	 * Cancels a booking that has already been refunded by the PaymentService.
	 * Skips the "Contact Support" check because the system initiated this.
	 *
	 * @param bookingId ID for the booking to be cancelled
	 */
	@Transactional
	public void cancelRefundedBooking(Long bookingId) {
		Booking booking = bookingRepository.findById(bookingId)
		                                   .orElseThrow(() -> new EntityNotFoundException("Booking not found."));

		// Logic: If we are here, the payment is already refunded, so we force cancel.
		handleBookingCancellation(booking);
	}

	/**
	 * Expire pending bookings for an event after a certain time.
	 * This handles when payment for a booking isn't made within a certain time
	 *
	 * @param cutoffTime the {@link Instant time} in the past from now to which all pending bookings should be
	 *                   cancelled
	 */
	@Transactional
	public void expirePendingBookings(Instant cutoffTime) {
		List<Booking> expiredBookings = bookingRepository.findByStatusAndCreatedAtBefore(
				BookingStatus.PENDING,
				cutoffTime
		);

		handleBookingCancellation(expiredBookings);
	}

	/**
	 * Expire pending bookings for an event that was cancelled.
	 *
	 * @param eventId ID of the event
	 */
	@Transactional
	public void expirePendingBookings(Long eventId) {
		List<Booking> pendingBookings = bookingRepository.findByStatusAndEventId(
				BookingStatus.PENDING,
				eventId
		);

		handleBookingCancellation(pendingBookings);
	}

	//When the booking expires or the event is cancelled
	/**
	 * INTERNAL USE ONLY.
	 * Processes a batch cancellation of pending or abandoned bookings.
	 * <p>
	 * Iterates through the provided list, transitioning each booking to an {@link BookingStatus#EXPIRED} state.
	 * It systematically communicates with the {@link SeatLockService} to gracefully release any lingering
	 * Redis distributed locks, detaches the seats from the booking, and returns them to the global
	 * {@link SeatStatus#AVAILABLE} pool for immediate resale.
	 * </p>
	 *
	 * @param expiredBookings A list of bookings that have timed out during checkout or belong to a cancelled event.
	 */
	private void handleBookingCancellation(List<Booking> expiredBookings) {
		if (expiredBookings.isEmpty()) return;

		List<Seat> seatsToUpdate = new ArrayList<>();

		for (Booking booking : expiredBookings) {
			booking.setStatus(BookingStatus.EXPIRED);

			for (Seat seat : booking.getSeats()) {
				seatLockService.releaseLock(seat.getId(), booking.getUser().getEmail());

				seat.setBooking(null);
				seat.setHeldByUser(null);
				seat.setHeldAt(null);
				seat.setSeatStatus(SeatStatus.AVAILABLE);
				seatsToUpdate.add(seat);
			}
		}

		seatRepository.saveAll(seatsToUpdate);
		bookingRepository.saveAll(expiredBookings);
	}

	//Booking cancelled by user, or successfully refunded
	/**
	 * INTERNAL USE ONLY.
	 * Processes the cancellation of a single booking, typically initiated by the user or triggered by a gateway refund.
	 * <p>
	 * Transitions the specific booking to a {@link BookingStatus#CANCELLED} state. It safely destroys the
	 * associated Redis seat locks, wipes the temporary user hold metadata, and restores the physical seats
	 * to the {@link SeatStatus#AVAILABLE} pool.
	 * </p>
	 *
	 * @param booking The specific booking entity to be cancelled.
	 */
	private void handleBookingCancellation(Booking booking) {
		booking.setStatus(BookingStatus.CANCELLED);

		List<Seat> seatsToRelease = booking.getSeats();
		for (Seat seat : seatsToRelease) {
			seatLockService.releaseLock(seat.getId(), booking.getUser().getEmail());

			seat.setSeatStatus(SeatStatus.AVAILABLE);
			seat.setHeldAt(null);
			seat.setHeldByUser(null);
			seat.setBooking(null);
		}

		seatRepository.saveAll(seatsToRelease);
		bookingRepository.save(booking);
	}

	private BookingResponse buildBookingResponse(Booking booking) {
		List<SeatResponse> seatResponses = booking.getSeats().stream()
		                                          .map(seat -> SeatResponse.builder()
		                                                                   .id(seat.getId())
		                                                                   .seatNumber(seat.getSeatNumber())
		                                                                   .rowName(seat.getRowName())
		                                                                   .status(seat.getSeatStatus()
		                                                                               .getDisplayName())
		                                                                   .build())
		                                          .toList();

		return BookingResponse.builder()
		                      .id(booking.getId())
		                      .bookingReference(booking.getBookingReference())
		                      .eventName(booking.getEvent().getName())
		                      .eventStartDateTime(booking.getEvent().getEventStartDateTime())
		                      .eventEndDateTime(booking.getEvent().getEventEndDateTime())
		                      .seats(seatResponses)
		                      .status(booking.getStatus().getDisplayName())
		                      .totalAmount(booking.getTotalAmount())
		                      .createdAt(booking.getCreatedAt())
		                      .build();
	}

	/**
	 * INTERNAL USE ONLY.
	 * Generates a cryptographically secure, collision-resistant public reference code for a new booking.
	 * <p>
	 * Utilizes a randomized generation strategy and verifies uniqueness against the database.
	 * To prevent thread-blocking infinite loops in a highly saturated database environment,
	 * it enforces a maximum of 4 generation attempts before explicitly failing.
	 * </p>
	 *
	 * @return A guaranteed unique, human-readable alphanumeric booking reference string.
	 * @throws IllegalStateException if a unique reference cannot be generated after the maximum number of retries.
	 */
	private String generateUniqueBookingReference() {
		int maxRetries = 4;
		int tries = 0;
		String bookingReference;
		do {
			bookingReference = BookingReferenceGenerator.generate();
			tries++;

			if (tries > maxRetries) {
				log.error("Max tries reached. Tried {} times out of {}.", tries, maxRetries);
				throw new IllegalStateException(
						"Failed to generate a unique booking reference. Maximum tries reached.");
			}
		} while (bookingRepository.existsByBookingReference(bookingReference));

		log.debug("Took {} tries to generate booking reference.", tries);
		return bookingReference;
	}
}
