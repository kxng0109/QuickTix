package io.github.kxng0109.quicktix.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.kxng0109.quicktix.dto.request.InitiateBookingRequest;
import io.github.kxng0109.quicktix.dto.response.BookingResponse;
import io.github.kxng0109.quicktix.dto.response.PaymentResponse;
import io.github.kxng0109.quicktix.entity.User;
import io.github.kxng0109.quicktix.enums.BookingStatus;
import io.github.kxng0109.quicktix.enums.PaymentMethod;
import io.github.kxng0109.quicktix.enums.PaymentStatus;
import io.github.kxng0109.quicktix.enums.Role;
import io.github.kxng0109.quicktix.exception.InvalidOperationException;
import io.github.kxng0109.quicktix.service.BookingService;
import io.github.kxng0109.quicktix.service.CustomUserDetailsService;
import io.github.kxng0109.quicktix.service.JwtService;
import io.github.kxng0109.quicktix.service.PaymentService;
import io.github.kxng0109.quicktix.utils.BookingReferenceGenerator;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookingController.class)
public class BookingControllerTest {

	private final Long bookingId = 100L;
	private final BigDecimal totalAmount = BigDecimal.valueOf(5000);
	private final ObjectMapper objectMapper = new ObjectMapper()
			.registerModule(new JavaTimeModule());

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private BookingService bookingService;

	@MockitoBean
	private PaymentService paymentService;

	@MockitoBean
	private JwtService jwtService;

	@MockitoBean
	private CustomUserDetailsService userDetailsService;

	private InitiateBookingRequest request;
	private BookingResponse response;
	private User currentUser;

	@BeforeEach
	public void setup() {
		request = InitiateBookingRequest.builder()
		                                .eventId(20L)
		                                .seats(List.of(10L, 11L))
		                                .totalAmount(totalAmount)
		                                .build();

		response = BookingResponse.builder()
		                          .id(bookingId)
		                          .bookingReference(BookingReferenceGenerator.generate())
		                          .eventName("Test Event")
		                          .eventStartDateTime(Instant.now().plus(2, ChronoUnit.DAYS))
		                          .eventEndDateTime(Instant.now().plus(2, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS))
		                          .status(BookingStatus.PENDING.getDisplayName())
		                          .totalAmount(totalAmount)
		                          .createdAt(Instant.now())
		                          .build();

		currentUser = User.builder()
		                  .id(1L)
		                  .email("test.user@quicktix.com")
		                  .role(Role.ADMIN)
		                  .passwordHash("hashed")
		                  .build();
	}

	@Test
	public void createBooking_should_return201Created_whenRequestIsValid() throws Exception {
		// Fix 1: use any(User.class) instead of eq(currentUser) — Spring Security wraps
		// the principal in a new instance so eq() will never match
		when(bookingService.createPendingBooking(any(InitiateBookingRequest.class), any(User.class)))
				.thenReturn(response);

		mockMvc.perform(
				       post("/api/v1/bookings")
						       .with(user(currentUser))
						       .contentType(MediaType.APPLICATION_JSON)
						       .content(objectMapper.writeValueAsString(request))
		       ).andExpect(status().isCreated())
		       .andExpect(jsonPath("$.id").value(bookingId))
		       .andExpect(jsonPath("$.status").value(BookingStatus.PENDING.getDisplayName()));
	}

	@Test
	public void createBooking_should_return400BadRequest_whenRequestIsInvalid() throws Exception {
		InitiateBookingRequest badRequest = InitiateBookingRequest.builder().build();

		mockMvc.perform(
				       post("/api/v1/bookings")
						       .with(user(currentUser))
						       .contentType(MediaType.APPLICATION_JSON)
						       .content(objectMapper.writeValueAsString(badRequest))
		       ).andExpect(status().isBadRequest())
		       .andExpect(jsonPath("$.eventId").value("Event ID is required"))
		       .andExpect(jsonPath("$.seats").value("Seats are required"));

		verify(bookingService, never()).createPendingBooking(any(InitiateBookingRequest.class), any(User.class));
	}

	@Test
	public void createBooking_should_return404NotFound_whenUserOrEventIsNotFound() throws Exception {
		when(bookingService.createPendingBooking(any(InitiateBookingRequest.class), any(User.class)))
				.thenThrow(EntityNotFoundException.class);

		mockMvc.perform(
				       post("/api/v1/bookings")
						       .with(user(currentUser))
						       .contentType(MediaType.APPLICATION_JSON)
						       .content(objectMapper.writeValueAsString(request))
		       ).andExpect(status().isNotFound())
		       .andExpect(jsonPath("$.statusCode").value(404))
		       .andExpect(jsonPath("$.path").value("/api/v1/bookings"));
	}

	@Test
	public void getBookingById_should_return200Ok_whenBookingExists() throws Exception {
		when(bookingService.getBookingById(eq(bookingId), any(User.class)))
				.thenReturn(response);

		mockMvc.perform(
				       get("/api/v1/bookings/{id}", bookingId)
						       .with(user(currentUser))
		       ).andExpect(status().isOk())
		       .andExpect(jsonPath("$.id").value(bookingId));
	}

	@Test
	public void getBookingById_should_return400BadRequest_whenIdIsInvalid() throws Exception {
		mockMvc.perform(
				       get("/api/v1/bookings/{id}", -1)
						       .with(user(currentUser))
		       ).andExpect(status().isBadRequest())
		       .andExpect(jsonPath("$.statusCode").value(400))
		       .andExpect(jsonPath("$.path").value("/api/v1/bookings/-1"));

		verify(bookingService, never()).getBookingById(anyLong(), any(User.class));
	}

	@Test
	public void getBookingById_should_return404NotFound_whenBookingIsNotFound() throws Exception {
		when(bookingService.getBookingById(eq(bookingId), any(User.class)))
				.thenThrow(EntityNotFoundException.class);

		mockMvc.perform(
				       get("/api/v1/bookings/{id}", bookingId)
						       .with(user(currentUser))
		       ).andExpect(status().isNotFound())
		       .andExpect(jsonPath("$.statusCode").value(404))
		       .andExpect(jsonPath("$.path").value("/api/v1/bookings/100"));
	}

	@Test
	public void getBookingByReference_should_return200Ok_whenBookingExists() throws Exception {
		String ref = response.bookingReference();
		when(bookingService.getBookingByReference(eq(ref), any(User.class)))
				.thenReturn(response);

		mockMvc.perform(
				       get("/api/v1/bookings/reference/{reference}", ref)
						       .with(user(currentUser))
		       ).andExpect(status().isOk())
		       .andExpect(jsonPath("$.bookingReference").value(ref));
	}

	@Test
	public void getBookingByReference_should_return400BadRequest_whenReferenceIsInvalid() throws Exception {
		mockMvc.perform(
				       get("/api/v1/bookings/reference/{reference}", " ")
						       .with(user(currentUser))
		       ).andExpect(status().isBadRequest())
		       .andExpect(jsonPath("$.statusCode").value(400))
		       .andExpect(jsonPath("$.path").value("/api/v1/bookings/reference/%20"));

		verify(bookingService, never()).getBookingByReference(anyString(), any(User.class));
	}

	@Test
	public void getBookingByReference_should_return404NotFound_whenBookingIsNotFound() throws Exception {
		String ref = response.bookingReference();
		when(bookingService.getBookingByReference(eq(ref), any(User.class)))
				.thenThrow(EntityNotFoundException.class);

		mockMvc.perform(
				       get("/api/v1/bookings/reference/{reference}", ref)
						       .with(user(currentUser))
		       ).andExpect(status().isNotFound())
		       .andExpect(jsonPath("$.statusCode").value(404))
		       .andExpect(jsonPath("$.path").value("/api/v1/bookings/reference/" + ref));
	}

	@Test
	public void getPaymentByBookingId_should_return200Ok_whenBookingAndPaymentExist() throws Exception {
		PaymentResponse paymentResponse = createPaymentResponse();
		when(paymentService.getPaymentByBookingId(eq(bookingId), any(User.class)))
				.thenReturn(paymentResponse);

		mockMvc.perform(
				       get("/api/v1/bookings/{id}/payment", bookingId)
						       .with(user(currentUser))
		       ).andExpect(status().isOk())
		       .andExpect(jsonPath("$.paymentId").value(paymentResponse.paymentId()))
		       .andExpect(jsonPath("$.status").value(paymentResponse.status()));
	}

	@Test
	public void getPaymentByBookingId_should_return400BadRequest_whenIdIsInvalid() throws Exception {
		mockMvc.perform(
				       get("/api/v1/bookings/{id}/payment", -1)
						       .with(user(currentUser))
		       ).andExpect(status().isBadRequest())
		       .andExpect(jsonPath("$.statusCode").value(400))
		       .andExpect(jsonPath("$.path").value("/api/v1/bookings/-1/payment"));

		verify(paymentService, never()).getPaymentByBookingId(anyLong(), any(User.class));
	}

	@Test
	public void getPaymentByBookingId_should_return404NotFound_whenBookingOrPaymentIsNotFound() throws Exception {
		when(paymentService.getPaymentByBookingId(eq(bookingId), any(User.class)))
				.thenThrow(EntityNotFoundException.class);

		mockMvc.perform(
				       get("/api/v1/bookings/{id}/payment", bookingId)
						       .with(user(currentUser))
		       ).andExpect(status().isNotFound())
		       .andExpect(jsonPath("$.statusCode").value(404))
		       .andExpect(jsonPath("$.path").value("/api/v1/bookings/100/payment"));
	}

	@Test
	public void cancelBookingById_should_return204NoContent_whenBookingIsCancelled() throws Exception {
		doNothing().when(bookingService).cancelBooking(eq(bookingId), any(User.class));

		mockMvc.perform(
				patch("/api/v1/bookings/{id}/cancel", bookingId)
						.with(user(currentUser))
						.contentType(MediaType.APPLICATION_JSON)
		).andExpect(status().isNoContent());

		verify(bookingService).cancelBooking(eq(bookingId), any(User.class));
	}

	@Test
	public void cancelBookingById_should_return400BadRequest_whenIdIsInvalid() throws Exception {
		mockMvc.perform(
				       patch("/api/v1/bookings/{id}/cancel", -1)
						       .with(user(currentUser))
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isBadRequest())
		       .andExpect(jsonPath("$.statusCode").value(400))
		       .andExpect(jsonPath("$.path").value("/api/v1/bookings/-1/cancel"));

		verify(bookingService, never()).cancelBooking(anyLong(), any(User.class));
	}

	@Test
	public void cancelBookingById_should_return404NotFound_whenBookingIsNotFound() throws Exception {
		doThrow(EntityNotFoundException.class).when(bookingService)
		                                      .cancelBooking(eq(bookingId), any(User.class));

		mockMvc.perform(
				       patch("/api/v1/bookings/{id}/cancel", bookingId)
						       .with(user(currentUser))
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isNotFound())
		       .andExpect(jsonPath("$.statusCode").value(404))
		       .andExpect(jsonPath("$.path").value("/api/v1/bookings/100/cancel"));
	}

	@Test
	public void cancelBookingById_should_return400BadRequest_whenBookingStatusIsNotPending() throws Exception {
		doThrow(InvalidOperationException.class).when(bookingService)
		                                        .cancelBooking(eq(bookingId), any(User.class));

		mockMvc.perform(
				       patch("/api/v1/bookings/{id}/cancel", bookingId)
						       .with(user(currentUser))
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isBadRequest())
		       .andExpect(jsonPath("$.statusCode").value(400))
		       .andExpect(jsonPath("$.path").value("/api/v1/bookings/100/cancel"));
	}

	@Test
	public void refundPayment_should_return204NoContent_whenPaymentIsRefunded() throws Exception {
		doNothing().when(paymentService).refundPayment(bookingId);

		mockMvc.perform(
				patch("/api/v1/bookings/{id}/refund", bookingId)
						.with(user(currentUser))
						.contentType(MediaType.APPLICATION_JSON)
		).andExpect(status().isNoContent());

		verify(paymentService).refundPayment(bookingId);
	}

	@Test
	public void refundPayment_should_return400BadRequest_whenIdIsInvalid() throws Exception {
		mockMvc.perform(
				       patch("/api/v1/bookings/{id}/refund", -1)
						       .with(user(currentUser))
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isBadRequest())
		       .andExpect(jsonPath("$.statusCode").value(400))
		       .andExpect(jsonPath("$.path").value("/api/v1/bookings/-1/refund"));

		verify(paymentService, never()).refundPayment(anyLong());
	}

	@Test
	public void refundPayment_should_return404NotFound_whenPaymentOrBookingIsNotFound() throws Exception {
		doThrow(EntityNotFoundException.class).when(paymentService)
		                                      .refundPayment(bookingId);

		mockMvc.perform(
				       patch("/api/v1/bookings/{id}/refund", bookingId)
						       .with(user(currentUser))
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isNotFound())
		       .andExpect(jsonPath("$.statusCode").value(404))
		       .andExpect(jsonPath("$.path").value("/api/v1/bookings/100/refund"));
	}

	@Test
	public void refundPayment_should_return400BadRequest_whenPaymentStatusIsNotCompleted() throws Exception {
		doThrow(InvalidOperationException.class).when(paymentService)
		                                        .refundPayment(bookingId);

		mockMvc.perform(
				       patch("/api/v1/bookings/{id}/refund", bookingId)
						       .with(user(currentUser))
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isBadRequest())
		       .andExpect(jsonPath("$.statusCode").value(400))
		       .andExpect(jsonPath("$.path").value("/api/v1/bookings/100/refund"));
	}

	private PaymentResponse createPaymentResponse() {
		return PaymentResponse.builder()
		                      .paymentId(400L)
		                      .amount(totalAmount)
		                      .status(PaymentStatus.COMPLETED.getDisplayName())
		                      .paymentMethod(PaymentMethod.CREDIT_CARD.getDisplayName())
		                      .paidAt(Instant.now())
		                      .build();
	}
}