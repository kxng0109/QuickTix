package io.github.kxng0109.quicktix.service;

import io.github.kxng0109.quicktix.dto.request.InitiateBookingRequest;
import io.github.kxng0109.quicktix.dto.response.BookingResponse;
import io.github.kxng0109.quicktix.entity.*;
import io.github.kxng0109.quicktix.enums.BookingStatus;
import io.github.kxng0109.quicktix.enums.PaymentStatus;
import io.github.kxng0109.quicktix.enums.SeatStatus;
import io.github.kxng0109.quicktix.exception.InvalidOperationException;
import io.github.kxng0109.quicktix.repositories.BookingRepository;
import io.github.kxng0109.quicktix.repositories.EventRepository;
import io.github.kxng0109.quicktix.repositories.SeatRepository;
import io.github.kxng0109.quicktix.repositories.UserRepository;
import io.github.kxng0109.quicktix.utils.BookingReferenceGenerator;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BookingService {

	private final BookingRepository bookingRepository;
	private final UserRepository userRepository;
	private final EventRepository eventRepository;
	private final SeatRepository seatRepository;
	private final SeatService seatService;

	@Transactional(readOnly = true)
	public BookingResponse getBookingById(Long bookId) {
		Booking booking = bookingRepository.findById(bookId)
		                                   .orElseThrow(
				                                   () -> new EntityNotFoundException("Booking not found.")
		                                   );

		//TODO: Spring security should handle this!
//		validateUserMadeBooking(userId, booking);

		return buildBookingResponse(booking);
	}

	@Transactional(readOnly = true)
	public BookingResponse getBookingByReference(String bookingReference) {
		Booking booking = bookingRepository.findByBookingReference(bookingReference)
		                                   .orElseThrow(
				                                   () -> new EntityNotFoundException("Booking not found.")
		                                   );

		//TODO: Once again, Spring Security
//		validateUserMadeBooking(userId, booking);

		return buildBookingResponse(booking);
	}

	@Transactional(readOnly = true)
	public Page<BookingResponse> getBookingsByUser(Long userId, Pageable pageable) {
		User user = userRepository.findById(userId)
		                          .orElseThrow(
				                          () -> new EntityNotFoundException("User not found.")
		                          );

		Page<Booking> bookings = bookingRepository.findByUserId(user.getId(), pageable);

		return bookings.map(this::buildBookingResponse);
	}

	@Transactional
	public BookingResponse createPendingBooking(InitiateBookingRequest request) {
		Event event = eventRepository.findById(request.eventId())
		                             .orElseThrow(
				                             () -> new EntityNotFoundException("Event not found.")
		                             );

		User user = userRepository.findById(request.userId())
		                          .orElseThrow(
				                          () -> new EntityNotFoundException("User not found.")
		                          );

		List<Seat> seats = seatService.validateAndGetHeldSeats(
				request.seats(),
				request.userId(),
				request.eventId()
		);

		Booking booking = Booking.builder()
		                         .user(user)
		                         .event(event)
		                         .seats(seats)
		                         .status(BookingStatus.PENDING)
		                         .bookingReference(generateUniqueBookingReference())
		                         .totalAmount(request.totalAmount())
		                         .build();

		Booking savedBooking = bookingRepository.save(booking);

		for (Seat seat : seats) {
			seat.setBooking(savedBooking);
		}
		seatRepository.saveAll(seats);

		return buildBookingResponse(savedBooking);
	}

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

	@Transactional
	public void cancelBooking(Long bookingId) {
		Booking booking = bookingRepository.findById(bookingId)
		                                   .orElseThrow(
				                                   () -> new EntityNotFoundException("Booking not found.")
		                                   );
		//TODO: Spring security once again babyyyy
//		validateUserMadeBooking(userId, booking);

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

	private void handleBookingCancellation(List<Booking> expiredBookings) {
		if (expiredBookings.isEmpty()) return;

		List<Seat> seatsToUpdate = new ArrayList<>();

		for (Booking booking : expiredBookings) {
			booking.setStatus(BookingStatus.EXPIRED);

			for (Seat seat : booking.getSeats()) {
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


	private void handleBookingCancellation(Booking booking) {
		booking.setStatus(BookingStatus.CANCELLED);

		List<Seat> seatsToRelease = booking.getSeats();
		for (Seat seat : seatsToRelease) {
			seat.setSeatStatus(SeatStatus.AVAILABLE);
			seat.setHeldAt(null);
			seat.setHeldByUser(null);
			seat.setBooking(null);
		}

		seatRepository.saveAll(seatsToRelease);
		bookingRepository.save(booking);
	}

	private void validateUserMadeBooking(Long userId, Booking booking) {
		if (!booking.getUser().getId().equals(userId)) {
			// I'm lying to the user by saying "Not Found" even though we found it.
			// This prevents them from guessing valid IDs.
			throw new EntityNotFoundException("Booking not found.");
		}
	}

	private BookingResponse buildBookingResponse(Booking booking) {
		return BookingResponse.builder()
		                      .id(booking.getId())
		                      .bookingReference(booking.getBookingReference())
		                      .eventName(booking.getEvent().getName())
		                      .eventStartDateTime(booking.getEvent().getEventStartDateTime())
		                      .eventEndDateTime(booking.getEvent().getEventEndDateTime())
		                      .seats(booking.getSeats())
		                      .status(booking.getStatus().getDisplayName())
		                      .totalAmount(booking.getTotalAmount())
		                      .createdAt(booking.getCreatedAt())
		                      .build();
	}

	private String generateUniqueBookingReference() {
		int maxRetries = 4;
		int tries = 0;
		String bookingReference;
		do {
			bookingReference = BookingReferenceGenerator.generate();
			tries++;

			if (tries > maxRetries) {
				throw new IllegalStateException(
						"Failed to generate a unique booking reference. Maximum tries reached.");
			}
		} while (bookingRepository.existsByBookingReference(bookingReference));

		return bookingReference;
	}
}
