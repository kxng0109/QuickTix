package io.github.kxng0109.quicktix.service;

import io.github.kxng0109.quicktix.dto.request.InitiateBookingRequest;
import io.github.kxng0109.quicktix.dto.response.BookingResponse;
import io.github.kxng0109.quicktix.entity.*;
import io.github.kxng0109.quicktix.enums.BookingStatus;
import io.github.kxng0109.quicktix.enums.EventStatus;
import io.github.kxng0109.quicktix.enums.PaymentStatus;
import io.github.kxng0109.quicktix.enums.SeatStatus;
import io.github.kxng0109.quicktix.exception.InvalidOperationException;
import io.github.kxng0109.quicktix.repositories.BookingRepository;
import io.github.kxng0109.quicktix.repositories.EventRepository;
import io.github.kxng0109.quicktix.repositories.SeatRepository;
import io.github.kxng0109.quicktix.repositories.UserRepository;
import io.github.kxng0109.quicktix.utils.BookingReferenceGenerator;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BookingServiceTest {

	private final Long bookingId = 100L;
	private final Long userId = 200L;
	private final Long paymentId = 300L;
	private final BigDecimal totalAmount = BigDecimal.valueOf(12334.54);
	private final Pageable pageable = PageRequest.of(0, 2);
	private final String bookingReference = BookingReferenceGenerator.generate();

	@Mock
	private BookingRepository bookingRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private EventRepository eventRepository;

	@Mock
	private SeatRepository seatRepository;

	@Mock
	private SeatService seatService;

	@InjectMocks
	private BookingService bookingService;

	private Booking booking;
	private User user;
	private Event event;
	private Seat seat1;
	private Seat seat2;
	private InitiateBookingRequest bookingRequest;

	@BeforeEach
	void setup() {
		user = User.builder()
		           .id(userId)
		           .firstName("John")
		           .lastName("Michael")
		           .email("john@michael.com")
		           .build();

		Long eventId = 300L;
		event = Event.builder()
		             .id(eventId)
		             .name("Lorem")
		             .description("Ipsum")
		             .venue(new Venue())
		             .eventStartDateTime(Instant.now().plus(2, ChronoUnit.HOURS))
		             .eventEndDateTime(Instant.now().plus(4, ChronoUnit.HOURS))
		             .ticketPrice(totalAmount)
		             .status(EventStatus.UPCOMING)
		             .seats(List.of())
		             .build();

		Long seat1Id = 400L;
		seat1 = Seat.builder()
		            .id(seat1Id)
		            .event(event)
		            .seatNumber(16)
		            .rowName("D")
		            .seatStatus(SeatStatus.HELD)
		            .heldAt(Instant.now().minus(5, ChronoUnit.MINUTES))
		            .heldByUser(user)
		            .build();

		Long seat2Id = 401L;
		seat2 = Seat.builder()
		            .id(seat2Id)
		            .event(event)
		            .seatNumber(25)
		            .rowName("S")
		            .seatStatus(SeatStatus.HELD)
		            .heldAt(Instant.now().minus(5, ChronoUnit.MINUTES))
		            .heldByUser(user)
		            .build();

		booking = Booking.builder()
		                 .id(bookingId)
		                 .user(user)
		                 .seats(List.of(seat1, seat2))
		                 .event(event)
		                 .status(BookingStatus.PENDING)
		                 .totalAmount(totalAmount)
		                 .build();

		seat1.setBooking(booking);
		user.setBookings(List.of(booking));
		event.setBookings(List.of(booking));
		booking.setPayment(
				Payment.builder()
				       .id(paymentId)
				       .booking(booking)
				       .status(PaymentStatus.COMPLETED)
				       .build()
		);

		bookingRequest = InitiateBookingRequest.builder()
		                                       .userId(userId)
		                                       .eventId(eventId)
		                                       .seats(List.of(seat1Id, seat2Id))
		                                       .totalAmount(booking.getTotalAmount())
		                                       .build();
	}

	@Test
	public void getBookingById_should_returnBookingResponse_when_bookingExists() {
		when(bookingRepository.findById(anyLong()))
				.thenReturn(Optional.of(booking));

		BookingResponse response = bookingService.getBookingById(bookingId);

		assertNotNull(response);
		assertEquals(bookingId, response.id());
		assertEquals(BookingStatus.PENDING.getDisplayName(), response.status());
		assertEquals(event.getName(), response.eventName());

		verify(bookingRepository).findById(anyLong());
	}

	@Test
	public void getBookingById_should_throwEntityNotFoundException_when_bookingIsNotFound() {
		when(bookingRepository.findById(anyLong()))
				.thenReturn(Optional.empty());

		assertThrows(
				EntityNotFoundException.class,
				() -> bookingService.getBookingById(bookingId)
		);

		verify(bookingRepository).findById(anyLong());
	}

	//TODO: Enable this when spring security is integrated
	@Disabled("Spring security logic needs to be integrated for this.")
	@Test
	public void getBookingById_should_throwEntityNotFoundException_when_userDoesNotOwnBooking() {
		when(bookingRepository.findById(anyLong()))
				.thenReturn(Optional.of(booking));

		assertThrows(
				EntityNotFoundException.class,
				() -> bookingService.getBookingById(bookingId)
		);

		verify(bookingRepository).findById(anyLong());
	}

	@Test
	public void getBookingByReference_should_returnBookingResponse_when_bookingExists() {
		when(bookingRepository.findByBookingReference(anyString()))
				.thenReturn(Optional.of(booking));

		BookingResponse response = bookingService.getBookingByReference(bookingReference);

		assertNotNull(response);
		assertEquals(bookingId, response.id());
		assertEquals(BookingStatus.PENDING.getDisplayName(), response.status());
		assertEquals(event.getName(), response.eventName());

		verify(bookingRepository).findByBookingReference(anyString());
	}

	//TODO: Enable this when spring security is integrated
	@Disabled("Spring security logic needs to be integrated for this.")
	@Test
	public void getBookingByReference_should_throwEntityNotFoundException_when_bookingIsNotFound() {
		when(bookingRepository.findByBookingReference(anyString()))
				.thenReturn(Optional.empty());

		assertThrows(
				EntityNotFoundException.class,
				() -> bookingService.getBookingByReference(bookingReference)
		);

		verify(bookingRepository).findByBookingReference(anyString());
	}

	//TODO: Enable this when spring security is integrated
	@Disabled("Spring security logic needs to be integrated for this.")
	@Test
	public void getBookingByReference_should_throwEntityNotFoundException_when_userDoesNotOwnBooking() {
		when(bookingRepository.findByBookingReference(anyString()))
				.thenReturn(Optional.of(booking));

		assertThrows(
				EntityNotFoundException.class,
				() -> bookingService.getBookingByReference(bookingReference)
		);

		verify(bookingRepository).findByBookingReference(anyString());
	}

	@Test
	public void getBookingsByUser_should_returnAPageOfBookingResponse_when_bookingExists() {
		Page<Booking> bookingPage = new PageImpl<>(List.of(booking));

		when(userRepository.findById(anyLong()))
				.thenReturn(Optional.of(user));
		when(bookingRepository.findByUserId(anyLong(), any(Pageable.class)))
				.thenReturn(bookingPage);

		Page<BookingResponse> responses = bookingService.getBookingsByUser(userId, pageable);

		assertNotNull(responses);
		assertEquals(bookingPage.getTotalElements(), responses.getTotalElements());
		assertEquals(booking.getEvent().getName(), responses.getContent().getFirst().eventName());

		verify(userRepository).findById(anyLong());
		verify(bookingRepository).findByUserId(anyLong(), any(Pageable.class));
	}

	@Test
	public void getBookingsByUser_should_throwEntityNotFoundException_when_userIsNotFound() {
		when(userRepository.findById(anyLong()))
				.thenReturn(Optional.empty());

		assertThrows(
				EntityNotFoundException.class,
				() -> bookingService.getBookingsByUser(userId, pageable)
		);

		verify(userRepository).findById(anyLong());
		verify(bookingRepository, never()).findByUserId(anyLong(), any(Pageable.class));
	}

	@Test
	public void createPendingBooking_should_createBookingAndReturnBookingResponse_whenEverythingIsValidAndAvailable() {
		when(eventRepository.findById(anyLong()))
				.thenReturn(Optional.of(event));
		when(userRepository.findById(anyLong()))
				.thenReturn(Optional.of(user));
		when(seatService.validateAndGetHeldSeats(anyList(), anyLong(), anyLong()))
				.thenReturn(List.of(seat1, seat2));
		when(bookingRepository.save(any(Booking.class)))
				.thenReturn(booking);

		BookingResponse response = bookingService.createPendingBooking(bookingRequest);

		assertNotNull(response);
		assertEquals(List.of(seat1, seat2), response.seats());
		assertEquals(totalAmount, response.totalAmount());

		verify(eventRepository).findById(anyLong());
		verify(userRepository).findById(anyLong());
		verify(seatService).validateAndGetHeldSeats(anyList(), anyLong(), anyLong());
		verify(bookingRepository).save(any(Booking.class));
		verify(seatRepository).saveAll(anyList());
	}

	@Test
	public void createPendingBooking_should_createBookingAndReturnBookingResponse_whenNoSeatAreHeldByUser() {
		booking.setSeats(List.of());

		when(eventRepository.findById(anyLong()))
				.thenReturn(Optional.of(event));
		when(userRepository.findById(anyLong()))
				.thenReturn(Optional.of(user));
		when(seatService.validateAndGetHeldSeats(anyList(), anyLong(), anyLong()))
				.thenReturn(List.of());
		when(bookingRepository.save(any(Booking.class)))
				.thenReturn(booking);

		BookingResponse response = bookingService.createPendingBooking(bookingRequest);

		assertNotNull(response);
		assertEquals(List.of(), response.seats());
		assertEquals(totalAmount, response.totalAmount());

		verify(eventRepository).findById(anyLong());
		verify(userRepository).findById(anyLong());
		verify(seatService).validateAndGetHeldSeats(anyList(), anyLong(), anyLong());
		verify(bookingRepository).save(any(Booking.class));
		verify(seatRepository).saveAll(anyList());
	}

	@Test
	public void createPendingBooking_should_throwEntityNotFoundException_when_eventIsNotFound() {
		when(eventRepository.findById(anyLong()))
				.thenReturn(Optional.empty());

		assertThrows(
				EntityNotFoundException.class,
				() -> bookingService.createPendingBooking(bookingRequest)
		);

		verify(eventRepository).findById(anyLong());
		verify(userRepository, never()).findById(anyLong());
		verify(seatService, never()).validateAndGetHeldSeats(anyList(), anyLong(), anyLong());
		verify(bookingRepository, never()).save(any(Booking.class));
		verify(seatRepository, never()).saveAll(anyList());
	}

	@Test
	public void createPendingBooking_should_throwEntityNotFoundException_when_userIsNotFound() {
		when(eventRepository.findById(anyLong()))
				.thenReturn(Optional.of(event));
		when(userRepository.findById(anyLong()))
				.thenReturn(Optional.empty());

		assertThrows(
				EntityNotFoundException.class,
				() -> bookingService.createPendingBooking(bookingRequest)
		);

		verify(eventRepository).findById(anyLong());
		verify(userRepository).findById(anyLong());
		verify(seatService, never()).validateAndGetHeldSeats(anyList(), anyLong(), anyLong());
		verify(bookingRepository, never()).save(any(Booking.class));
		verify(seatRepository, never()).saveAll(anyList());
	}

	@Test
	public void createPendingBooking_should_throwEntityNotFoundException_when_seatsAreInvalid() {
		when(eventRepository.findById(anyLong()))
				.thenReturn(Optional.of(event));
		when(userRepository.findById(anyLong()))
				.thenReturn(Optional.of(user));
		when(seatService.validateAndGetHeldSeats(anyList(), anyLong(), anyLong()))
				.thenThrow(EntityNotFoundException.class);

		assertThrows(
				EntityNotFoundException.class,
				() -> bookingService.createPendingBooking(bookingRequest)
		);

		verify(eventRepository).findById(anyLong());
		verify(userRepository).findById(anyLong());
		verify(seatService).validateAndGetHeldSeats(anyList(), anyLong(), anyLong());
	}

	@Test
	public void createPendingBooking_should_throwIllegalArgumentException_when_seatDoesNotBelongToEvent() {
		when(eventRepository.findById(anyLong()))
				.thenReturn(Optional.of(event));
		when(userRepository.findById(anyLong()))
				.thenReturn(Optional.of(user));
		when(seatService.validateAndGetHeldSeats(anyList(), anyLong(), anyLong()))
				.thenThrow(IllegalArgumentException.class);

		assertThrows(
				IllegalArgumentException.class,
				() -> bookingService.createPendingBooking(bookingRequest)
		);

		verify(eventRepository).findById(anyLong());
		verify(userRepository).findById(anyLong());
		verify(seatService).validateAndGetHeldSeats(anyList(), anyLong(), anyLong());
	}

	@Test
	public void createPendingBooking_should_throwIllegalStateException_when_seatIsNotAvailable() {
		when(eventRepository.findById(anyLong()))
				.thenReturn(Optional.of(event));
		when(userRepository.findById(anyLong()))
				.thenReturn(Optional.of(user));
		when(seatService.validateAndGetHeldSeats(anyList(), anyLong(), anyLong()))
				.thenThrow(IllegalStateException.class);

		assertThrows(
				IllegalStateException.class,
				() -> bookingService.createPendingBooking(bookingRequest)
		);

		verify(eventRepository).findById(anyLong());
		verify(userRepository).findById(anyLong());
		verify(seatService).validateAndGetHeldSeats(anyList(), anyLong(), anyLong());
	}

	@Test
	public void confirmBooking_should_returnNothing_when_bookingIsPending() {
		when(bookingRepository.findByIdWithPayment(anyLong()))
				.thenReturn(Optional.of(booking));
		when(bookingRepository.save(any(Booking.class)))
				.thenReturn(booking);

		bookingService.confirmBooking(bookingId);

		assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
		assertEquals(SeatStatus.BOOKED, booking.getSeats().getFirst().getSeatStatus());
		assertEquals(paymentId, booking.getPayment().getId());

		verify(bookingRepository).findByIdWithPayment(anyLong());
		verify(bookingRepository).save(any(Booking.class));
		verify(seatRepository).saveAll(anyList());

	}

	@Test
	public void confirmBooking_should_returnNothing_when_bookingIsAlreadyConfirmed() {
		booking.setStatus(BookingStatus.CONFIRMED);

		when(bookingRepository.findByIdWithPayment(anyLong()))
				.thenReturn(Optional.of(booking));

		bookingService.confirmBooking(bookingId);

		assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
		assertEquals(SeatStatus.HELD,
		             booking.getSeats().getFirst().getSeatStatus()
		); //Intentional to show that it didn't get to the logic under it

		verify(bookingRepository).findByIdWithPayment(anyLong());
		verify(seatRepository, never()).saveAll(anyList());
		verify(bookingRepository, never()).save(any(Booking.class));
	}

	@Test
	public void confirmBooking_should_throwEntityNotFoundException_when_bookingIsNotFound() {
		when(bookingRepository.findByIdWithPayment(anyLong()))
				.thenReturn(Optional.empty());

		assertThrows(
				EntityNotFoundException.class,
				() -> bookingService.confirmBooking(bookingId)
		);

		verify(bookingRepository).findByIdWithPayment(anyLong());
		verify(seatRepository, never()).saveAll(anyList());
		verify(bookingRepository, never()).save(any(Booking.class));
	}

	@Test
	public void confirmBooking_should_throwEntityNotFoundException_when_bookingIsNotPendingOrConfirmed() {
		booking.setStatus(BookingStatus.EXPIRED);

		when(bookingRepository.findByIdWithPayment(anyLong()))
				.thenReturn(Optional.of(booking));

		EntityNotFoundException ex = assertThrows(
				EntityNotFoundException.class,
				() -> bookingService.confirmBooking(bookingId)
		);

		assertEquals("Cannot confirm a booking that is in status: " + booking.getStatus(), ex.getMessage());

		verify(bookingRepository).findByIdWithPayment(anyLong());
		verify(seatRepository, never()).saveAll(anyList());
		verify(bookingRepository, never()).save(any(Booking.class));
	}

	@Test
	public void confirmBooking_should_throwInvalidOperationException_when_paymentIsNullOrNotCompleted() {
		booking.setPayment(null);

		when(bookingRepository.findByIdWithPayment(anyLong()))
				.thenReturn(Optional.of(booking));

		InvalidOperationException ex = assertThrows(
				InvalidOperationException.class,
				() -> bookingService.confirmBooking(bookingId)
		);

		assertEquals(
				"Cannot confirm a booking. Payment is missing or not completed",
				ex.getMessage()
		);
		assertEquals(BookingStatus.PENDING, booking.getStatus());

		verify(bookingRepository).findByIdWithPayment(anyLong());
		verify(seatRepository, never()).saveAll(anyList());
		verify(bookingRepository, never()).save(any(Booking.class));
	}

	@Test
	public void cancelBooking_should_cancelBooking_when_userMadeBooking() {
		when(bookingRepository.findById(anyLong()))
				.thenReturn(Optional.of(booking));

		bookingService.cancelBooking(bookingId);

		assertEquals(BookingStatus.CANCELLED, booking.getStatus());
		assertEquals(SeatStatus.AVAILABLE, booking.getSeats().getFirst().getSeatStatus());

		verify(bookingRepository).findById(anyLong());
		verify(seatRepository).saveAll(anyList());
		verify(bookingRepository).save(any(Booking.class));
	}

	@Test
	public void cancelBooking_should_throwInvalidOperationException_when_bookingStatusIsConfirmedOrNotPending() {
		booking.setStatus(BookingStatus.CONFIRMED);

		when(bookingRepository.findById(anyLong()))
				.thenReturn(Optional.of(booking));

		assertThrows(
				InvalidOperationException.class,
				() -> bookingService.cancelBooking(bookingId)
		);

		assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
		assertEquals(SeatStatus.HELD, booking.getSeats().getFirst().getSeatStatus());

		verify(bookingRepository).findById(anyLong());
		verify(seatRepository, never()).saveAll(anyList());
		verify(bookingRepository, never()).save(any(Booking.class));
	}

	@Test
	public void cancelBooking_should_throwEntityNotFoundException_when_bookingIsNotFound() {
		when(bookingRepository.findById(anyLong()))
				.thenReturn(Optional.empty());

		assertThrows(
				EntityNotFoundException.class,
				() -> bookingService.cancelBooking(bookingId)
		);

		verify(bookingRepository).findById(anyLong());
		verify(seatRepository, never()).saveAll(anyList());
		verify(bookingRepository, never()).save(any(Booking.class));
	}

	//TODO: Enable this when spring security is integrated
	@Disabled("Spring security logic needs to be integrated for this.")
	@Test
	public void cancelBooking_should_throwEntityNotFoundException_when_userDoesNotOwnBooking() {
		when(bookingRepository.findById(anyLong()))
				.thenReturn(Optional.of(booking));

		assertThrows(
				EntityNotFoundException.class,
				() -> bookingService.cancelBooking(bookingId)
		);

		verify(bookingRepository).findById(anyLong());
		verify(seatRepository, never()).saveAll(anyList());
		verify(bookingRepository, never()).save(any(Booking.class));
	}

	@Test
	public void expirePendingBookings_should_cancelBooking_when_bookingHasBeenPendingForAWhile() {
		when(bookingRepository.findByStatusAndCreatedAtBefore(
				eq(BookingStatus.PENDING),
				any(Instant.class)
		)).thenReturn(List.of(booking));

		bookingService.expirePendingBookings(Instant.now().minus(20, ChronoUnit.MINUTES));

		assertEquals(BookingStatus.EXPIRED, booking.getStatus());
		assertEquals(SeatStatus.AVAILABLE, booking.getSeats().getFirst().getSeatStatus());

		verify(bookingRepository).findByStatusAndCreatedAtBefore(
				eq(BookingStatus.PENDING),
				any(Instant.class)
		);
		verify(seatRepository).saveAll(anyList());
		verify(bookingRepository).saveAll(anyList());
	}

	@Test
	public void expirePendingBookings_should_cancelBooking_when_noBookingsArePending() {
		when(bookingRepository.findByStatusAndCreatedAtBefore(
				eq(BookingStatus.PENDING),
				any(Instant.class)
		)).thenReturn(List.of());

		bookingService.expirePendingBookings(Instant.now().minus(20, ChronoUnit.MINUTES));

		assertEquals(BookingStatus.PENDING, booking.getStatus());
		assertEquals(SeatStatus.HELD, booking.getSeats().getFirst().getSeatStatus());

		verify(bookingRepository).findByStatusAndCreatedAtBefore(
				eq(BookingStatus.PENDING),
				any(Instant.class)
		);
		verify(seatRepository, never()).saveAll(anyList());
		verify(bookingRepository, never()).saveAll(anyList());
	}

	@Test
	public void cancelRefundedBooking_should_cancelBooking_evenWhenStatusIsConfirmed() {
		booking.setStatus(BookingStatus.CONFIRMED);

		when(bookingRepository.findById(anyLong()))
				.thenReturn(Optional.of(booking));

		bookingService.cancelRefundedBooking(bookingId);

		assertEquals(BookingStatus.CANCELLED, booking.getStatus());
		assertEquals(SeatStatus.AVAILABLE, booking.getSeats().getFirst().getSeatStatus());

		verify(bookingRepository).findById(bookingId);
		verify(seatRepository).saveAll(anyList());
		verify(bookingRepository).save(any(Booking.class));
	}

	@Test
	public void cancelRefundedBooking_should_throwEntityNotFoundException_when_bookingIsNotFound() {
		when(bookingRepository.findById(anyLong()))
				.thenReturn(Optional.empty());

		assertThrows(
				EntityNotFoundException.class,
				() -> bookingService.cancelRefundedBooking(bookingId)
		);

		verify(bookingRepository).findById(bookingId);
		verify(bookingRepository, never()).save(any(Booking.class));
	}
}
