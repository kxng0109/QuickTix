package io.github.kxng0109.quicktix.integration;

import io.github.kxng0109.quicktix.dto.request.*;
import io.github.kxng0109.quicktix.entity.Booking;
import io.github.kxng0109.quicktix.entity.Seat;
import io.github.kxng0109.quicktix.entity.User;
import io.github.kxng0109.quicktix.enums.BookingStatus;
import io.github.kxng0109.quicktix.enums.SeatStatus;
import io.github.kxng0109.quicktix.repositories.BookingRepository;
import io.github.kxng0109.quicktix.repositories.SeatRepository;
import io.github.kxng0109.quicktix.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class BookingFlowIntegrationTest extends BaseIntegrationTest {

	@Autowired
	private SeatRepository seatRepository;

	@Autowired
	private BookingRepository bookingRepository;

	@Autowired
	private UserRepository userRepository;

	@Test
	void completeBookingFlow_shouldWorkEndToEnd() throws Exception {
		CreateVenueRequest venueRequest = CreateVenueRequest.builder()
		                                                    .name("Test Arena")
		                                                    .address("123 Test Street")
		                                                    .city("TestCity")
		                                                    .totalCapacity(100)
		                                                    .build();

		MvcResult venueResult = mockMvc.perform(post("/api/v1/venues")
				                                        .with(user("admin@test.com").roles("ADMIN"))
				                                        .contentType(MediaType.APPLICATION_JSON)
				                                        .content(objectMapper.writeValueAsString(venueRequest)))
		                               .andExpect(status().isCreated())
		                               .andReturn();

		Long venueId = objectMapper.readTree(venueResult.getResponse().getContentAsString()).get("id").asLong();

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
				                                        .with(user("admin@test.com").roles("ADMIN"))
				                                        .contentType(MediaType.APPLICATION_JSON)
				                                        .content(objectMapper.writeValueAsString(eventRequest)))
		                               .andExpect(status().isCreated())
		                               .andExpect(jsonPath("$.availableSeats").value(10))
		                               .andReturn();

		Long eventId = objectMapper.readTree(eventResult.getResponse().getContentAsString()).get("id").asLong();

		List<Seat> allSeats = seatRepository.findAll();
		assertThat(allSeats).hasSize(10);
		assertThat(allSeats).allMatch(s -> s.getSeatStatus() == SeatStatus.AVAILABLE);

		CreateUserRequest registerRequest = CreateUserRequest.builder()
		                                                     .firstName("Test")
		                                                     .lastName("User")
		                                                     .email("test.user@example.com")
		                                                     .password("password123")
		                                                     .phoneNumber("+2341111111111")
		                                                     .build();

		mockMvc.perform(post("/api/v1/auth/register")
				                .contentType(MediaType.APPLICATION_JSON)
				                .content(objectMapper.writeValueAsString(registerRequest)))
		       .andExpect(status().isCreated());

		// Fix: load the saved entity so @AuthenticationPrincipal resolves to the custom
		// User type instead of null. .with(user(String)) sets a Spring Security internal
		// User principal which doesn't match the custom entity type.
		User testUser = userRepository.findByEmail("test.user@example.com").orElseThrow();

		List<Long> seatIds = allSeats.subList(0, 3).stream().map(Seat::getId).toList();

		HoldSeatsRequest holdRequest = HoldSeatsRequest.builder()
		                                               .eventId(eventId)
		                                               .userId(testUser.getId())
		                                               .seatIds(seatIds)
		                                               .build();

		mockMvc.perform(post("/api/v1/seats/hold")
				                .with(user(testUser))
				                .contentType(MediaType.APPLICATION_JSON)
				                .content(objectMapper.writeValueAsString(holdRequest)))
		       .andExpect(status().isCreated())
		       .andExpect(jsonPath("$.length()").value(3));

		List<Seat> heldSeats = seatRepository.findAllById(seatIds);
		assertThat(heldSeats).allMatch(s -> s.getSeatStatus() == SeatStatus.HELD);
		assertThat(heldSeats).allMatch(s -> s.getHeldByUser().getId().equals(testUser.getId()));

		BigDecimal totalAmount = BigDecimal.valueOf(5000).multiply(BigDecimal.valueOf(3));

		InitiateBookingRequest bookingRequest = InitiateBookingRequest.builder()
		                                                              .eventId(eventId)
		                                                              .seats(seatIds)
		                                                              .totalAmount(totalAmount)
		                                                              .build();

		MvcResult bookingResult = mockMvc.perform(post("/api/v1/bookings")
				                                          .with(user(testUser))
				                                          .contentType(MediaType.APPLICATION_JSON)
				                                          .content(objectMapper.writeValueAsString(bookingRequest)))
		                                 .andExpect(status().isCreated())
		                                 .andExpect(jsonPath("$.status").value("Pending"))
		                                 .andExpect(jsonPath("$.totalAmount").value(15000))
		                                 .andExpect(jsonPath("$.bookingReference").exists())
		                                 .andReturn();

		Long bookingId = objectMapper.readTree(bookingResult.getResponse().getContentAsString()).get("id").asLong();

		Booking savedBooking = bookingRepository.findByIdWithSeats(bookingId).orElse(null);
		assertThat(savedBooking).isNotNull();
		assertThat(savedBooking.getStatus()).isEqualTo(BookingStatus.PENDING);
		assertThat(savedBooking.getSeats()).hasSize(3);
		assertThat(savedBooking.getTotalAmount()).isEqualByComparingTo(totalAmount);

		List<Seat> bookedSeats = seatRepository.findAllById(seatIds);
		assertThat(bookedSeats).allMatch(s -> s.getBooking() != null);
		assertThat(bookedSeats).allMatch(s -> s.getBooking().getId().equals(bookingId));

		long availableCount = seatRepository.findAll().stream()
		                                    .filter(s -> s.getSeatStatus() == SeatStatus.AVAILABLE)
		                                    .count();
		assertThat(availableCount).isEqualTo(7);
	}

	@Test
	void holdSeats_shouldFail_whenSeatsAlreadyHeld() throws Exception {
		MvcResult venueResult = mockMvc.perform(post("/api/v1/venues")
				                                        .with(user("admin@test.com").roles("ADMIN"))
				                                        .contentType(MediaType.APPLICATION_JSON)
				                                        .content(objectMapper.writeValueAsString(
						                                        CreateVenueRequest.builder()
						                                                          .name("Lock Test Arena")
						                                                          .address("456 Lock Street")
						                                                          .city("LockCity")
						                                                          .totalCapacity(50)
						                                                          .build())))
		                               .andExpect(status().isCreated())
		                               .andReturn();
		Long venueId = objectMapper.readTree(venueResult.getResponse().getContentAsString()).get("id").asLong();

		MvcResult eventResult = mockMvc.perform(post("/api/v1/events")
				                                        .with(user("admin@test.com").roles("ADMIN"))
				                                        .contentType(MediaType.APPLICATION_JSON)
				                                        .content(objectMapper.writeValueAsString(
						                                        CreateEventRequest.builder()
						                                                          .name("Lock Test Event")
						                                                          .description("Testing locks")
						                                                          .venueId(venueId)
						                                                          .eventStartDateTime(Instant.now()
						                                                                                     .plus(5,
						                                                                                           ChronoUnit.DAYS
						                                                                                     ))
						                                                          .eventEndDateTime(Instant.now()
						                                                                                   .plus(5,
						                                                                                         ChronoUnit.DAYS
						                                                                                   )
						                                                                                   .plus(2,
						                                                                                         ChronoUnit.HOURS
						                                                                                   ))
						                                                          .ticketPrice(BigDecimal.valueOf(1000))
						                                                          .numberOfSeats(5)
						                                                          .build())))
		                               .andExpect(status().isCreated())
		                               .andReturn();
		Long eventId = objectMapper.readTree(eventResult.getResponse().getContentAsString()).get("id").asLong();

		mockMvc.perform(post("/api/v1/auth/register")
				                .contentType(MediaType.APPLICATION_JSON)
				                .content(objectMapper.writeValueAsString(
						                CreateUserRequest.builder()
						                                 .firstName("User")
						                                 .lastName("One")
						                                 .email("user1@test.com")
						                                 .password("password123")
						                                 .phoneNumber("+2342222222222")
						                                 .build())))
		       .andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/auth/register")
				                .contentType(MediaType.APPLICATION_JSON)
				                .content(objectMapper.writeValueAsString(
						                CreateUserRequest.builder()
						                                 .firstName("User")
						                                 .lastName("Two")
						                                 .email("user2@test.com")
						                                 .password("password123")
						                                 .phoneNumber("+2343333333333")
						                                 .build())))
		       .andExpect(status().isCreated());

		// Fix: load entities so @AuthenticationPrincipal resolves correctly
		User user1 = userRepository.findByEmail("user1@test.com").orElseThrow();
		User user2 = userRepository.findByEmail("user2@test.com").orElseThrow();

		List<Long> seatIds = seatRepository.findAll().stream()
		                                   .filter(s -> s.getEvent().getId().equals(eventId))
		                                   .map(Seat::getId)
		                                   .limit(2)
		                                   .toList();

		HoldSeatsRequest holdRequest1 = HoldSeatsRequest.builder()
		                                                .eventId(eventId)
		                                                .userId(user1.getId())
		                                                .seatIds(seatIds)
		                                                .build();

		mockMvc.perform(post("/api/v1/seats/hold")
				                .with(user(user1))
				                .contentType(MediaType.APPLICATION_JSON)
				                .content(objectMapper.writeValueAsString(holdRequest1)))
		       .andExpect(status().isCreated());

		HoldSeatsRequest holdRequest2 = HoldSeatsRequest.builder()
		                                                .eventId(eventId)
		                                                .userId(user2.getId())
		                                                .seatIds(seatIds)
		                                                .build();

		mockMvc.perform(post("/api/v1/seats/hold")
				                .with(user(user2))
				                .contentType(MediaType.APPLICATION_JSON)
				                .content(objectMapper.writeValueAsString(holdRequest2)))
		       .andExpect(status().isBadRequest());

		List<Seat> seats = seatRepository.findAllById(seatIds);
		assertThat(seats).allMatch(s -> s.getHeldByUser().getId().equals(user1.getId()));
	}

	@Test
	void cancelBooking_shouldReleaseSeats() throws Exception {
		MvcResult venueResult = mockMvc.perform(post("/api/v1/venues")
				                                        .with(user("admin@test.com").roles("ADMIN"))
				                                        .contentType(MediaType.APPLICATION_JSON)
				                                        .content(objectMapper.writeValueAsString(
						                                        CreateVenueRequest.builder()
						                                                          .name("Cancel Test Venue")
						                                                          .address("Addr")
						                                                          .city("City")
						                                                          .totalCapacity(20)
						                                                          .build())))
		                               .andExpect(status().isCreated())
		                               .andReturn();
		Long venueId = objectMapper.readTree(venueResult.getResponse().getContentAsString()).get("id").asLong();

		MvcResult eventResult = mockMvc.perform(post("/api/v1/events")
				                                        .with(user("admin@test.com").roles("ADMIN"))
				                                        .contentType(MediaType.APPLICATION_JSON)
				                                        .content(objectMapper.writeValueAsString(
						                                        CreateEventRequest.builder()
						                                                          .name("Cancel Test Event")
						                                                          .description("Desc")
						                                                          .venueId(venueId)
						                                                          .eventStartDateTime(Instant.now()
						                                                                                     .plus(3,
						                                                                                           ChronoUnit.DAYS
						                                                                                     ))
						                                                          .eventEndDateTime(Instant.now()
						                                                                                   .plus(3,
						                                                                                         ChronoUnit.DAYS
						                                                                                   )
						                                                                                   .plus(1,
						                                                                                         ChronoUnit.HOURS
						                                                                                   ))
						                                                          .ticketPrice(BigDecimal.valueOf(2000))
						                                                          .numberOfSeats(5)
						                                                          .build())))
		                               .andExpect(status().isCreated())
		                               .andReturn();
		Long eventId = objectMapper.readTree(eventResult.getResponse().getContentAsString()).get("id").asLong();

		mockMvc.perform(post("/api/v1/auth/register")
				                .contentType(MediaType.APPLICATION_JSON)
				                .content(objectMapper.writeValueAsString(
						                CreateUserRequest.builder()
						                                 .firstName("Cancel")
						                                 .lastName("Tester")
						                                 .email("cancel@test.com")
						                                 .password("password123")
						                                 .phoneNumber("+2344444444444")
						                                 .build())))
		       .andExpect(status().isCreated());

		// Fix: load entity so @AuthenticationPrincipal resolves correctly
		User cancelUser = userRepository.findByEmail("cancel@test.com").orElseThrow();

		List<Long> seatIds = seatRepository.findAll().stream()
		                                   .filter(s -> s.getEvent().getId().equals(eventId))
		                                   .map(Seat::getId)
		                                   .limit(2)
		                                   .toList();

		mockMvc.perform(post("/api/v1/seats/hold")
				                .with(user(cancelUser))
				                .contentType(MediaType.APPLICATION_JSON)
				                .content(objectMapper.writeValueAsString(
						                HoldSeatsRequest.builder()
						                                .eventId(eventId)
						                                .userId(cancelUser.getId())
						                                .seatIds(seatIds)
						                                .build())))
		       .andExpect(status().isCreated());

		MvcResult bookingResult = mockMvc.perform(post("/api/v1/bookings")
				                                          .with(user(cancelUser))
				                                          .contentType(MediaType.APPLICATION_JSON)
				                                          .content(objectMapper.writeValueAsString(
						                                          InitiateBookingRequest.builder()
						                                                                .eventId(eventId)
						                                                                .seats(seatIds)
						                                                                .totalAmount(BigDecimal.valueOf(
								                                                                4000))
						                                                                .build())))
		                                 .andExpect(status().isCreated())
		                                 .andReturn();
		Long bookingId = objectMapper.readTree(bookingResult.getResponse().getContentAsString()).get("id").asLong();

		Booking booking = bookingRepository.findById(bookingId).orElseThrow();
		assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);

		mockMvc.perform(patch("/api/v1/bookings/{id}/cancel", bookingId)
				                .with(user(cancelUser)))
		       .andExpect(status().isNoContent());

		Booking cancelledBooking = bookingRepository.findById(bookingId).orElseThrow();
		assertThat(cancelledBooking.getStatus()).isEqualTo(BookingStatus.CANCELLED);

		List<Seat> releasedSeats = seatRepository.findAllById(seatIds);
		assertThat(releasedSeats).allMatch(s -> s.getSeatStatus() == SeatStatus.AVAILABLE);
		assertThat(releasedSeats).allMatch(s -> s.getHeldByUser() == null);
		assertThat(releasedSeats).allMatch(s -> s.getBooking() == null);
	}
}