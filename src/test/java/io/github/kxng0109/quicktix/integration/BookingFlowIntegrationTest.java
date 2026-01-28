package io.github.kxng0109.quicktix.integration;

import io.github.kxng0109.quicktix.dto.request.*;
import io.github.kxng0109.quicktix.entity.Booking;
import io.github.kxng0109.quicktix.entity.Seat;
import io.github.kxng0109.quicktix.enums.BookingStatus;
import io.github.kxng0109.quicktix.enums.SeatStatus;
import io.github.kxng0109.quicktix.repositories.BookingRepository;
import io.github.kxng0109.quicktix.repositories.SeatRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for the complete booking flow.
 * <p>
 * This test simulates a real user journey:
 * 1. Create a venue
 * 2. Create an event at that venue
 * 3. Create a user
 * 4. User holds seats
 * 5. User creates a booking
 * 6. Verify all database states
 *
 */
public class BookingFlowIntegrationTest extends BaseIntegrationTest {

	@Autowired
	private SeatRepository seatRepository;

	@Autowired
	private BookingRepository bookingRepository;

	@Test
	void completeBookingFlow_shouldWorkEndToEnd() throws Exception {
		//Create a venue
		CreateVenueRequest venueRequest = CreateVenueRequest.builder()
		                                                    .name("Test Arena")
		                                                    .address("123 Test Street")
		                                                    .city("TestCity")
		                                                    .totalCapacity(100)
		                                                    .build();

		MvcResult venueResult = mockMvc.perform(post("/api/v1/venues")
				                                        .contentType(MediaType.APPLICATION_JSON)
				                                        .content(objectMapper.writeValueAsString(venueRequest)))
		                               .andExpect(status().isCreated())
		                               .andReturn();

		Long venueId = objectMapper.readTree(venueResult.getResponse().getContentAsString())
		                           .get("id").asLong();

		//Create an event
		CreateEventRequest eventRequest = CreateEventRequest.builder()
		                                                    .name("Test Concert")
		                                                    .description("A test concert")
		                                                    .venueId(venueId)
		                                                    .eventStartDateTime(Instant.now().plus(7, ChronoUnit.DAYS))
		                                                    .eventEndDateTime(Instant.now().plus(7, ChronoUnit.DAYS)
		                                                                             .plus(3, ChronoUnit.HOURS))
		                                                    .ticketPrice(BigDecimal.valueOf(5000))
		                                                    .numberOfSeats(10)
		                                                    .build();

		MvcResult eventResult = mockMvc.perform(post("/api/v1/events")
				                                        .contentType(MediaType.APPLICATION_JSON)
				                                        .content(objectMapper.writeValueAsString(eventRequest)))
		                               .andExpect(status().isCreated())
		                               .andExpect(jsonPath("$.availableSeats").value(10))
		                               .andReturn();

		Long eventId = objectMapper.readTree(eventResult.getResponse().getContentAsString())
		                           .get("id").asLong();

		// Verify seats were created
		List<Seat> allSeats = seatRepository.findAll();
		assertThat(allSeats).hasSize(10);
		assertThat(allSeats).allMatch(s -> s.getSeatStatus() == SeatStatus.AVAILABLE);

		//Create a user
		CreateUserRequest userRequest = CreateUserRequest.builder()
		                                                 .firstName("Test")
		                                                 .lastName("User")
		                                                 .email("test.user@example.com")
		                                                 .build();

		MvcResult userResult = mockMvc.perform(post("/api/v1/users")
				                                       .contentType(MediaType.APPLICATION_JSON)
				                                       .content(objectMapper.writeValueAsString(userRequest)))
		                              .andExpect(status().isCreated())
		                              .andReturn();

		Long userId = objectMapper.readTree(userResult.getResponse().getContentAsString())
		                          .get("id").asLong();

		//User holds seats
		//Get seat IDs (first 3 seats)
		List<Long> seatIds = allSeats.subList(0, 3).stream()
		                             .map(Seat::getId)
		                             .toList();

		HoldSeatsRequest holdRequest = HoldSeatsRequest.builder()
		                                               .eventId(eventId)
		                                               .userId(userId)
		                                               .seatIds(seatIds)
		                                               .build();

		mockMvc.perform(post("/api/v1/seats/hold")
				                .contentType(MediaType.APPLICATION_JSON)
				                .content(objectMapper.writeValueAsString(holdRequest)))
		       .andExpect(status().isCreated())
		       .andExpect(jsonPath("$.length()").value(3));

		// Verify seats are now HELD in database
		List<Seat> heldSeats = seatRepository.findAllById(seatIds);
		assertThat(heldSeats).allMatch(s -> s.getSeatStatus() == SeatStatus.HELD);
		assertThat(heldSeats).allMatch(s -> s.getHeldByUser().getId().equals(userId));

		//Create booking
		BigDecimal totalAmount = BigDecimal.valueOf(5000).multiply(BigDecimal.valueOf(3)); // 3 seats

		InitiateBookingRequest bookingRequest = InitiateBookingRequest.builder()
		                                                              .userId(userId)
		                                                              .eventId(eventId)
		                                                              .seats(seatIds)
		                                                              .totalAmount(totalAmount)
		                                                              .build();

		MvcResult bookingResult = mockMvc.perform(post("/api/v1/bookings")
				                                          .contentType(MediaType.APPLICATION_JSON)
				                                          .content(objectMapper.writeValueAsString(bookingRequest)))
		                                 .andExpect(status().isCreated())
		                                 .andExpect(jsonPath("$.status").value("Pending"))
		                                 .andExpect(jsonPath("$.totalAmount").value(15000))
		                                 .andExpect(jsonPath("$.bookingReference").exists())
		                                 .andReturn();

		Long bookingId = objectMapper.readTree(bookingResult.getResponse().getContentAsString())
		                             .get("id").asLong();

		// Verify booking exists and is PENDING
		Booking savedBooking = bookingRepository.findByIdWithSeats(bookingId).orElse(null);
		assertThat(savedBooking).isNotNull();
		assertThat(savedBooking.getStatus()).isEqualTo(BookingStatus.PENDING);

		assertThat(savedBooking.getSeats()).hasSize(3);
		assertThat(savedBooking.getTotalAmount()).isEqualByComparingTo(totalAmount);

		// Verify seats are linked to booking
		List<Seat> bookedSeats = seatRepository.findAllById(seatIds);
		assertThat(bookedSeats).allMatch(s -> s.getBooking() != null);
		assertThat(bookedSeats).allMatch(s -> s.getBooking().getId().equals(bookingId));

		// Verify remaining seats are still available
		long availableCount = seatRepository.findAll().stream()
		                                    .filter(s -> s.getSeatStatus() == SeatStatus.AVAILABLE)
		                                    .count();
		assertThat(availableCount).isEqualTo(7); // 10 total - 3 held
	}

	@Test
	void holdSeats_shouldFail_whenSeatsAlreadyHeld() throws Exception {
		// This test verifies the pessimistic locking behavior

		// Setup: Create venue, event, and two users
		CreateVenueRequest venueRequest = CreateVenueRequest.builder()
		                                                    .name("Lock Test Arena")
		                                                    .address("456 Lock Street")
		                                                    .city("LockCity")
		                                                    .totalCapacity(50)
		                                                    .build();

		MvcResult venueResult = mockMvc.perform(post("/api/v1/venues")
				                                        .contentType(MediaType.APPLICATION_JSON)
				                                        .content(objectMapper.writeValueAsString(venueRequest)))
		                               .andExpect(status().isCreated())
		                               .andReturn();
		Long venueId = objectMapper.readTree(venueResult.getResponse().getContentAsString()).get("id").asLong();

		CreateEventRequest eventRequest = CreateEventRequest.builder()
		                                                    .name("Lock Test Event")
		                                                    .description("Testing locks")
		                                                    .venueId(venueId)
		                                                    .eventStartDateTime(Instant.now().plus(5, ChronoUnit.DAYS))
		                                                    .eventEndDateTime(Instant.now().plus(5, ChronoUnit.DAYS)
		                                                                             .plus(2, ChronoUnit.HOURS))
		                                                    .ticketPrice(BigDecimal.valueOf(1000))
		                                                    .numberOfSeats(5)
		                                                    .build();

		MvcResult eventResult = mockMvc.perform(post("/api/v1/events")
				                                        .contentType(MediaType.APPLICATION_JSON)
				                                        .content(objectMapper.writeValueAsString(eventRequest)))
		                               .andExpect(status().isCreated())
		                               .andReturn();
		Long eventId = objectMapper.readTree(eventResult.getResponse().getContentAsString()).get("id").asLong();

		// Create User 1
		MvcResult user1Result = mockMvc.perform(post("/api/v1/users")
				                                        .contentType(MediaType.APPLICATION_JSON)
				                                        .content(objectMapper.writeValueAsString(
						                                        CreateUserRequest.builder()
						                                                         .firstName("User").lastName("One")
						                                                         .email("user1@test.com").build())))
		                               .andExpect(status().isCreated())
		                               .andReturn();
		Long user1Id = objectMapper.readTree(user1Result.getResponse().getContentAsString()).get("id").asLong();

		// Create User 2
		MvcResult user2Result = mockMvc.perform(post("/api/v1/users")
				                                        .contentType(MediaType.APPLICATION_JSON)
				                                        .content(objectMapper.writeValueAsString(
						                                        CreateUserRequest.builder()
						                                                         .firstName("User").lastName("Two")
						                                                         .email("user2@test.com").build())))
		                               .andExpect(status().isCreated())
		                               .andReturn();
		Long user2Id = objectMapper.readTree(user2Result.getResponse().getContentAsString()).get("id").asLong();

		// Get seat IDs
		List<Long> seatIds = seatRepository.findAll().stream()
		                                   .filter(s -> s.getEvent().getId().equals(eventId))
		                                   .map(Seat::getId)
		                                   .limit(2)
		                                   .toList();

		// User 1 holds the seats
		HoldSeatsRequest holdRequest1 = HoldSeatsRequest.builder()
		                                                .eventId(eventId)
		                                                .userId(user1Id)
		                                                .seatIds(seatIds)
		                                                .build();

		mockMvc.perform(post("/api/v1/seats/hold")
				                .contentType(MediaType.APPLICATION_JSON)
				                .content(objectMapper.writeValueAsString(holdRequest1)))
		       .andExpect(status().isCreated());

		// User 2 tries to hold the same seats — should fail
		HoldSeatsRequest holdRequest2 = HoldSeatsRequest.builder()
		                                                .eventId(eventId)
		                                                .userId(user2Id)
		                                                .seatIds(seatIds)
		                                                .build();

		mockMvc.perform(post("/api/v1/seats/hold")
				                .contentType(MediaType.APPLICATION_JSON)
				                .content(objectMapper.writeValueAsString(holdRequest2)))
		       .andExpect(status().isBadRequest());

		// Verify seats are still held by User 1
		List<Seat> seats = seatRepository.findAllById(seatIds);
		assertThat(seats).allMatch(s -> s.getHeldByUser().getId().equals(user1Id));
	}

	@Test
	void cancelBooking_shouldReleaseSeats() throws Exception {
		// Setup: Create full booking flow first

		MvcResult venueResult = mockMvc.perform(post("/api/v1/venues")
				                                        .contentType(MediaType.APPLICATION_JSON)
				                                        .content(objectMapper.writeValueAsString(
						                                        CreateVenueRequest.builder()
						                                                          .name("Cancel Test Venue")
						                                                          .address("Addr").city("City")
						                                                          .totalCapacity(20).build())))
		                               .andExpect(status().isCreated())
		                               .andReturn();
		Long venueId = objectMapper.readTree(venueResult.getResponse().getContentAsString()).get("id").asLong();

		MvcResult eventResult = mockMvc.perform(post("/api/v1/events")
				                                        .contentType(MediaType.APPLICATION_JSON)
				                                        .content(objectMapper.writeValueAsString(
						                                        CreateEventRequest.builder()
						                                                          .name("Cancel Test Event")
						                                                          .description("Desc").venueId(venueId)
						                                                          .eventStartDateTime(Instant.now()
						                                                                                     .plus(3,
						                                                                                           ChronoUnit.DAYS
						                                                                                     ))
						                                                          .eventEndDateTime(Instant.now()
						                                                                                   .plus(3,
						                                                                                         ChronoUnit.DAYS
						                                                                                   ).plus(1,
						                                                                                          ChronoUnit.HOURS
								                                                          ))
						                                                          .ticketPrice(BigDecimal.valueOf(2000))
						                                                          .numberOfSeats(5).build())))
		                               .andExpect(status().isCreated())
		                               .andReturn();
		Long eventId = objectMapper.readTree(eventResult.getResponse().getContentAsString()).get("id").asLong();

		MvcResult userResult = mockMvc.perform(post("/api/v1/users")
				                                       .contentType(MediaType.APPLICATION_JSON)
				                                       .content(objectMapper.writeValueAsString(
						                                       CreateUserRequest.builder()
						                                                        .firstName("Cancel").lastName("Tester")
						                                                        .email("cancel@test.com").build())))
		                              .andExpect(status().isCreated())
		                              .andReturn();
		Long userId = objectMapper.readTree(userResult.getResponse().getContentAsString()).get("id").asLong();

		List<Long> seatIds = seatRepository.findAll().stream()
		                                   .filter(s -> s.getEvent().getId().equals(eventId))
		                                   .map(Seat::getId)
		                                   .limit(2)
		                                   .toList();

		// Hold seats
		mockMvc.perform(post("/api/v1/seats/hold")
				                .contentType(MediaType.APPLICATION_JSON)
				                .content(objectMapper.writeValueAsString(
						                HoldSeatsRequest.builder().eventId(eventId).userId(userId).seatIds(seatIds)
						                                .build())))
		       .andExpect(status().isCreated());

		// Create booking
		MvcResult bookingResult = mockMvc.perform(post("/api/v1/bookings")
				                                          .contentType(MediaType.APPLICATION_JSON)
				                                          .content(objectMapper.writeValueAsString(
						                                          InitiateBookingRequest.builder()
						                                                                .userId(userId).eventId(eventId)
						                                                                .seats(seatIds)
						                                                                .totalAmount(BigDecimal.valueOf(
								                                                                4000)).build())))
		                                 .andExpect(status().isCreated())
		                                 .andReturn();
		Long bookingId = objectMapper.readTree(bookingResult.getResponse().getContentAsString()).get("id").asLong();

		// Verify booking is PENDING and seats are held
		Booking booking = bookingRepository.findById(bookingId).orElseThrow();
		assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);

		// Cancel the booking
		mockMvc.perform(patch("/api/v1/bookings/{id}/cancel", bookingId))
		       .andExpect(status().isNoContent());

		// Verify booking is CANCELLED
		Booking cancelledBooking = bookingRepository.findById(bookingId).orElseThrow();
		assertThat(cancelledBooking.getStatus()).isEqualTo(BookingStatus.CANCELLED);

		// Verify seats are AVAILABLE again
		List<Seat> releasedSeats = seatRepository.findAllById(seatIds);
		assertThat(releasedSeats).allMatch(s -> s.getSeatStatus() == SeatStatus.AVAILABLE);
		assertThat(releasedSeats).allMatch(s -> s.getHeldByUser() == null);
		assertThat(releasedSeats).allMatch(s -> s.getBooking() == null);
	}
}