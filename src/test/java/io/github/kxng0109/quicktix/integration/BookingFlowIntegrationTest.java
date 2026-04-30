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
import io.github.kxng0109.quicktix.service.SeatLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
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

	@MockitoBean
	private SeatLockService seatLockService;

	@BeforeEach
	void setUp() {
		lenient().when(seatLockService.acquireLock(anyLong(), anyString())).thenReturn(true);
	}

	@Test
	void completeBookingFlow_shouldWorkEndToEnd() throws Exception {
		CreateVenueRequest venueRequest = CreateVenueRequest.builder()
		                                                    .name("Test Arena")
		                                                    .address("123 Test Street")
		                                                    .city("TestCity")
		                                                    .totalCapacity(100)
		                                                    .build();

		String adminToken = getAdminToken();

		MvcResult venueResult = mockMvc.perform(post("/api/v1/venues")
				                                        .header("Authorization", "Bearer " + adminToken)
				                                        .contentType(MediaType.APPLICATION_JSON)
				                                        .content(objectMapper.writeValueAsString(venueRequest)))
		                               .andExpect(status().isCreated())
		                               .andReturn();

		Long venueId = objectMapper.readTree(venueResult.getResponse().getContentAsString()).get("id").asLong();

		RowRequest rowRequest = RowRequest.builder()
		                                  .name("A")
		                                  .rowOrder(1)
		                                  .numberOfSeats(5)
		                                  .build();

		SectionRequest sectionRequest = SectionRequest.builder()
		                                              .name("VIP")
		                                              .description("VIP section")
		                                              .capacity(100)
		                                              .basePrice(BigDecimal.valueOf(5000.00))
		                                              .rows(List.of(rowRequest))
		                                              .build();

		CreateEventRequest eventRequest = CreateEventRequest.builder()
		                                                    .name("Test Concert")
		                                                    .description("A test concert")
		                                                    .venueId(venueId)
		                                                    .eventStartDateTime(Instant.now().plus(7, ChronoUnit.DAYS))
		                                                    .eventEndDateTime(Instant.now().plus(7, ChronoUnit.DAYS)
		                                                                             .plus(3, ChronoUnit.HOURS))
		                                                    .sections(List.of(sectionRequest))
		                                                    .numberOfSeats(10L)
		                                                    .build();

		MvcResult eventResult = mockMvc.perform(post("/api/v1/events")
				                                        .header("Authorization", "Bearer " + adminToken)
				                                        .contentType(MediaType.APPLICATION_JSON)
				                                        .content(objectMapper.writeValueAsString(eventRequest)))
		                               .andExpect(status().isCreated())
		                               .andExpect(jsonPath("$.availableSeats").value(5))
		                               .andReturn();

		Long eventId = objectMapper.readTree(eventResult.getResponse().getContentAsString()).get("id").asLong();

		List<Seat> allSeats = seatRepository.findAll();
		assertThat(allSeats).hasSize(5);
		assertThat(allSeats).allMatch(s -> s.getSeatStatus() == SeatStatus.AVAILABLE);

		CreateUserRequest registerRequest = CreateUserRequest.builder()
		                                                     .firstName("Test")
		                                                     .lastName("User")
		                                                     .email("test.user@example.com")
		                                                     .password("password123")
		                                                     .phoneNumber("+2341111111111")
		                                                     .build();

		MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
				                                           .contentType(MediaType.APPLICATION_JSON)
				                                           .content(objectMapper.writeValueAsString(registerRequest)))
		                                  .andExpect(status().isCreated())
		                                  .andReturn();

		String responseBody = registerResult.getResponse().getContentAsString();
		String userToken = objectMapper.readTree(responseBody).get("token").asText();

		User testUser = userRepository.findByEmail("test.user@example.com").orElseThrow();

		List<Long> seatIds = allSeats.subList(0, 3).stream().map(Seat::getId).toList();

		HoldSeatsRequest holdRequest = HoldSeatsRequest.builder()
		                                               .eventId(eventId)
		                                               .seatIds(seatIds)
		                                               .build();

		mockMvc.perform(post("/api/v1/seats/hold")
				                .contentType(MediaType.APPLICATION_JSON)
				                .header("Authorization", "Bearer " + userToken)
				                .content(objectMapper.writeValueAsString(holdRequest)))
		       .andExpect(status().isCreated())
		       .andExpect(jsonPath("$.length()").value(3));

		List<Seat> heldSeats = seatRepository.findAllById(seatIds);
		assertThat(heldSeats).allMatch(s -> s.getSeatStatus() == SeatStatus.HELD);
		assertThat(heldSeats).allMatch(s -> s.getHeldByUser().getId().equals(testUser.getId()));

		BigDecimal totalAmount = BigDecimal.valueOf(5000).multiply(BigDecimal.valueOf(3));

		InitiateBookingRequest bookingRequest = InitiateBookingRequest.builder()
		                                                              .eventId(eventId)
		                                                              .seatIds(seatIds)
		                                                              .build();

		MvcResult bookingResult = mockMvc.perform(post("/api/v1/bookings")
				                                          .contentType(MediaType.APPLICATION_JSON)
				                                          .header("Authorization", "Bearer " + userToken)
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
		assertThat(availableCount).isEqualTo(2);
	}

	@Test
	void holdSeats_shouldFail_whenSeatsAlreadyHeld() throws Exception {
		String adminToken = getAdminToken();

		MvcResult venueResult = mockMvc.perform(post("/api/v1/venues")
				                                        .header("Authorization", "Bearer " + adminToken)
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


		RowRequest rowRequest = RowRequest.builder()
		                                  .name("A")
		                                  .rowOrder(1)
		                                  .numberOfSeats(5)
		                                  .build();

		SectionRequest sectionRequest = SectionRequest.builder()
		                                              .name("VIP")
		                                              .description("VIP section")
		                                              .capacity(100)
		                                              .basePrice(BigDecimal.valueOf(5000.00))
		                                              .rows(List.of(rowRequest))
		                                              .build();

		CreateEventRequest createEventRequest = CreateEventRequest.builder()
		                                                          .name("Lock Test Event")
		                                                          .description("Testing locks")
		                                                          .venueId(venueId)
		                                                          .eventStartDateTime(Instant.now()
		                                                                                     .plus(5, ChronoUnit.DAYS))
		                                                          .eventEndDateTime(Instant.now()
		                                                                                   .plus(5, ChronoUnit.DAYS)
		                                                                                   .plus(2, ChronoUnit.HOURS))
		                                                          .sections(List.of(sectionRequest))
		                                                          .numberOfSeats(5L)
		                                                          .build();

		MvcResult eventResult = mockMvc.perform(post("/api/v1/events")
				                                        .header("Authorization", "Bearer " + adminToken)
				                                        .contentType(MediaType.APPLICATION_JSON)
				                                        .content(objectMapper.writeValueAsString(createEventRequest)))
		                               .andExpect(status().isCreated())
		                               .andReturn();
		Long eventId = objectMapper.readTree(eventResult.getResponse().getContentAsString()).get("id").asLong();

		MvcResult user1Result = mockMvc.perform(post("/api/v1/auth/register")
				                                        .contentType(MediaType.APPLICATION_JSON)
				                                        .content(objectMapper.writeValueAsString(
						                                        CreateUserRequest.builder()
						                                                         .firstName("User")
						                                                         .lastName("One")
						                                                         .email("user1@test.com")
						                                                         .password("password123")
						                                                         .phoneNumber("+2342222222222")
						                                                         .build())))
		                               .andExpect(status().isCreated())
		                               .andReturn();

		MvcResult user2Result = mockMvc.perform(post("/api/v1/auth/register")
				                                        .contentType(MediaType.APPLICATION_JSON)
				                                        .content(objectMapper.writeValueAsString(
						                                        CreateUserRequest.builder()
						                                                         .firstName("User")
						                                                         .lastName("Two")
						                                                         .email("user2@test.com")
						                                                         .password("password123")
						                                                         .phoneNumber("+2343333333333")
						                                                         .build())))
		                               .andExpect(status().isCreated())
		                               .andReturn();

		String user1Token = extractTokenFromMvcResult(user1Result);
		String user2Token = extractTokenFromMvcResult(user2Result);

		List<Long> seatIds = seatRepository.findAll().stream()
		                                   .filter(s -> s.getEvent().getId().equals(eventId))
		                                   .map(Seat::getId)
		                                   .limit(2)
		                                   .toList();

		HoldSeatsRequest holdRequest1 = HoldSeatsRequest.builder()
		                                                .eventId(eventId)
		                                                .seatIds(seatIds)
		                                                .build();

		mockMvc.perform(post("/api/v1/seats/hold")
				                .header("Authorization", "Bearer " + user1Token)
				                .contentType(MediaType.APPLICATION_JSON)
				                .content(objectMapper.writeValueAsString(holdRequest1)))
		       .andExpect(status().isCreated());

		HoldSeatsRequest holdRequest2 = HoldSeatsRequest.builder()
		                                                .eventId(eventId)
		                                                .seatIds(seatIds)
		                                                .build();

		mockMvc.perform(post("/api/v1/seats/hold")
				                .header("Authorization", "Bearer " + user2Token)
				                .contentType(MediaType.APPLICATION_JSON)
				                .content(objectMapper.writeValueAsString(holdRequest2)))
		       .andExpect(status().isBadRequest());

		List<Seat> seats = seatRepository.findAllById(seatIds);
//		assertThat(seats).allMatch(s -> s.getHeldByUser().getId().equals());
	}

	@Test
	void cancelBooking_shouldReleaseSeats() throws Exception {
		String adminToken = getAdminToken();

		MvcResult venueResult = mockMvc.perform(post("/api/v1/venues")
				                                        .header("Authorization", "Bearer " + adminToken)
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


		RowRequest rowRequest = RowRequest.builder()
		                                  .name("A")
		                                  .rowOrder(1)
		                                  .numberOfSeats(5)
		                                  .build();

		SectionRequest sectionRequest = SectionRequest.builder()
		                                              .name("VIP")
		                                              .description("VIP section")
		                                              .capacity(100)
		                                              .basePrice(BigDecimal.valueOf(5000.00))
		                                              .rows(List.of(rowRequest))
		                                              .build();

		CreateEventRequest createEventRequest = CreateEventRequest.builder()
		                                                          .name("Cancel Test Event")
		                                                          .description("Desc")
		                                                          .venueId(venueId)
		                                                          .eventStartDateTime(
				                                                          Instant.now().plus(3, ChronoUnit.DAYS))
		                                                          .eventEndDateTime(Instant.now()
		                                                                                   .plus(3, ChronoUnit.DAYS)
		                                                                                   .plus(1, ChronoUnit.HOURS))
		                                                          .sections(List.of(sectionRequest))
		                                                          .numberOfSeats(5L)
		                                                          .build();

		MvcResult eventResult = mockMvc.perform(post("/api/v1/events")
				                                        .header("Authorization", "Bearer " + adminToken)
				                                        .contentType(MediaType.APPLICATION_JSON)
				                                        .content(objectMapper.writeValueAsString(createEventRequest)))
		                               .andExpect(status().isCreated())
		                               .andReturn();
		Long eventId = objectMapper.readTree(eventResult.getResponse().getContentAsString()).get("id").asLong();

		MvcResult cancelUserResult = mockMvc.perform(post("/api/v1/auth/register")
				                                             .contentType(MediaType.APPLICATION_JSON)
				                                             .content(objectMapper.writeValueAsString(
						                                             CreateUserRequest.builder()
						                                                              .firstName("Cancel")
						                                                              .lastName("Tester")
						                                                              .email("cancel@test.com")
						                                                              .password("password123")
						                                                              .phoneNumber("+2344444444444")
						                                                              .build())))
		                                    .andExpect(status().isCreated())
		                                    .andReturn();

		String userToken = extractTokenFromMvcResult(cancelUserResult);

		List<Long> seatIds = seatRepository.findAll().stream()
		                                   .filter(s -> s.getEvent().getId().equals(eventId))
		                                   .map(Seat::getId)
		                                   .limit(2)
		                                   .toList();

		mockMvc.perform(post("/api/v1/seats/hold")
				                .header("Authorization", "Bearer " + userToken)
				                .contentType(MediaType.APPLICATION_JSON)
				                .content(objectMapper.writeValueAsString(
						                HoldSeatsRequest.builder()
						                                .eventId(eventId)
						                                .seatIds(seatIds)
						                                .build())))
		       .andExpect(status().isCreated());

		MvcResult bookingResult = mockMvc.perform(post("/api/v1/bookings")
				                                          .header("Authorization", "Bearer " + userToken)
				                                          .contentType(MediaType.APPLICATION_JSON)
				                                          .content(objectMapper.writeValueAsString(
						                                          InitiateBookingRequest.builder()
						                                                                .eventId(eventId)
						                                                                .seatIds(seatIds)
						                                                                .build())))
		                                 .andExpect(status().isCreated())
		                                 .andReturn();
		Long bookingId = objectMapper.readTree(bookingResult.getResponse().getContentAsString()).get("id").asLong();

		Booking booking = bookingRepository.findById(bookingId).orElseThrow();
		assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);

		mockMvc.perform(patch("/api/v1/bookings/{id}/cancel", bookingId)
				                .header("Authorization", "Bearer " + userToken))
		       .andExpect(status().isNoContent());

		Booking cancelledBooking = bookingRepository.findById(bookingId).orElseThrow();
		assertThat(cancelledBooking.getStatus()).isEqualTo(BookingStatus.CANCELLED);

		List<Seat> releasedSeats = seatRepository.findAllById(seatIds);
		assertThat(releasedSeats).allMatch(s -> s.getSeatStatus() == SeatStatus.AVAILABLE);
		assertThat(releasedSeats).allMatch(s -> s.getHeldByUser() == null);
		assertThat(releasedSeats).allMatch(s -> s.getBooking() == null);
	}

	private String extractTokenFromMvcResult(MvcResult mvcResult) throws UnsupportedEncodingException {
		return objectMapper.readTree(mvcResult.getResponse().getContentAsString()).get("token").asText();
	}
}