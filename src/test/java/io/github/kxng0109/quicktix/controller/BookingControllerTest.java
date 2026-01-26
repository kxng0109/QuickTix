package io.github.kxng0109.quicktix.controller;

import io.github.kxng0109.quicktix.dto.request.InitiateBookingRequest;
import io.github.kxng0109.quicktix.dto.response.BookingResponse;
import io.github.kxng0109.quicktix.dto.response.PaymentResponse;
import io.github.kxng0109.quicktix.enums.BookingStatus;
import io.github.kxng0109.quicktix.enums.PaymentMethod;
import io.github.kxng0109.quicktix.enums.PaymentStatus;
import io.github.kxng0109.quicktix.exception.InvalidOperationException;
import io.github.kxng0109.quicktix.service.BookingService;
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
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookingController.class)
public class BookingControllerTest {

	private final InitiateBookingRequest badRequest = InitiateBookingRequest.builder().build();
	private final String bookingReference = BookingReferenceGenerator.generate();
	private final Long bookingId = 100L;
	private final List<Long> seatsId = List.of(301L, 302L, 303L);
	private final BigDecimal totalAmount = BigDecimal.valueOf(12345.68);


	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private BookingService bookingService;

	@MockitoBean
	private PaymentService paymentService;

	private InitiateBookingRequest request;
	private BookingResponse response;

	@BeforeEach
	public void setup() {
		Long userId = 200L;
		Long eventId = 300L;
		request = InitiateBookingRequest.builder()
		                                .userId(userId)
		                                .eventId(eventId)
		                                .seats(seatsId)
		                                .totalAmount(totalAmount)
		                                .build();

		response = BookingResponse.builder()
		                          .id(bookingId)
		                          .bookingReference(bookingReference)
		                          .eventName("An event name")
		                          .eventStartDateTime(Instant.now().plus(1, ChronoUnit.HOURS))
		                          .eventEndDateTime(Instant.now().plus(2, ChronoUnit.HOURS))
		                          .status(BookingStatus.PENDING.getDisplayName())
		                          .totalAmount(totalAmount)
		                          .build();
	}

	@Test
	public void createBooking_should_return201CreatedAndBookingResponse_whenRequestIsValid() throws Exception {
		when(bookingService.createPendingBooking(any(InitiateBookingRequest.class)))
				.thenReturn(response);

		mockMvc.perform(
				       post("/api/v1/bookings")
						       .contentType(MediaType.APPLICATION_JSON)
						       .content(objectMapper.writeValueAsString(request))
		       ).andExpect(status().isCreated())
		       .andExpect(jsonPath("$.id").value(bookingId))
		       .andExpect(jsonPath("$.bookingReference").value(bookingReference))
		       .andExpect(jsonPath("$.totalAmount").value(totalAmount));
	}

	@Test
	public void createBooking_should_return400BadRequest_whenRequestIsInvalid() throws Exception {
		mockMvc.perform(
				       post("/api/v1/bookings")
						       .contentType(MediaType.APPLICATION_JSON)
						       .content(objectMapper.writeValueAsString(badRequest))
		       ).andExpect(status().isBadRequest())
		       .andExpect(jsonPath("$.userId").value("User ID is required"))
		       .andExpect(jsonPath("$.eventId").value("Event ID is required"))
		       .andExpect(jsonPath("$.seats").value("Seats are required"));

		verify(bookingService, never()).createPendingBooking(any(InitiateBookingRequest.class));
	}

	@Test
	public void createBooking_should_return404NotFound_whenEventOrUserIsNotFound() throws Exception {
		doThrow(EntityNotFoundException.class).when(bookingService)
		                                      .createPendingBooking(any(InitiateBookingRequest.class));

		mockMvc.perform(
				       post("/api/v1/bookings")
						       .contentType(MediaType.APPLICATION_JSON)
						       .content(objectMapper.writeValueAsString(request))
		       ).andExpect(status().isNotFound())
		       .andExpect(jsonPath("$.statusCode").value(404))
		       .andExpect(jsonPath("$.path").value("/api/v1/bookings"));
	}

	@Test
	public void getBookingById_should_return200OkAndBookingResponse_whenIdIsValid() throws Exception {
		when(bookingService.getBookingById(anyLong()))
				.thenReturn(response);

		mockMvc.perform(
				       get("/api/v1/bookings/{id}", bookingId)
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isOk())
		       .andExpect(jsonPath("$.id").value(bookingId))
		       .andExpect(jsonPath("$.bookingReference").value(bookingReference))
		       .andExpect(jsonPath("$.totalAmount").value(totalAmount));
	}

	@Test
	public void getBookingById_should_return400BadRequest_whenIdIsInvalid() throws Exception {
		mockMvc.perform(
				       get("/api/v1/bookings/{id}", -1)
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isBadRequest())
		       .andExpect(jsonPath("$.statusCode").value(400))
		       .andExpect(jsonPath("$.path").value("/api/v1/bookings/-1"));

		verify(bookingService, never()).getBookingById(anyLong());
	}

	@Test
	public void getBookingById_should_return404NotFound_whenBookingIsNotFound() throws Exception {
		doThrow(EntityNotFoundException.class).when(bookingService)
		                                      .getBookingById(anyLong());

		mockMvc.perform(
				       get("/api/v1/bookings/{id}", bookingId)
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isNotFound())
		       .andExpect(jsonPath("$.statusCode").value(404))
		       .andExpect(jsonPath("$.path").value("/api/v1/bookings/100"));
	}

	@Test
	public void getBookingByReference_should_return200OkAndBookingResponse_whenReferenceIsValid() throws Exception {
		when(bookingService.getBookingByReference(anyString()))
				.thenReturn(response);

		mockMvc.perform(
				       get("/api/v1/bookings/reference/{reference}", bookingReference)
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isOk())
		       .andExpect(jsonPath("$.id").value(bookingId))
		       .andExpect(jsonPath("$.bookingReference").value(bookingReference))
		       .andExpect(jsonPath("$.totalAmount").value(totalAmount));
	}

	@Test
	public void getBookingByReference_should_return400BadRequest_whenReferenceIsInvalid() throws Exception {
		mockMvc.perform(
				       get("/api/v1/bookings/reference/{reference}", " ")
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isBadRequest())
		       .andExpect(jsonPath("$.statusCode").value(400))
		       .andExpect(jsonPath("$.path").value("/api/v1/bookings/reference/%20"));

		verify(bookingService, never()).getBookingByReference(anyString());
	}

	@Test
	public void getBookingByReference_should_return404NotFound_whenBookingIsNotFound() throws Exception {
		doThrow(EntityNotFoundException.class).when(bookingService)
		                                      .getBookingByReference(anyString());

		mockMvc.perform(
				       get("/api/v1/bookings/reference/{reference}", bookingReference)
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isNotFound())
		       .andExpect(jsonPath("$.statusCode").value(404))
		       .andExpect(jsonPath("$.path").value("/api/v1/bookings/reference/" + bookingReference));
	}

	@Test
	public void getPaymentByBookingId_should_return200OkAndBookingResponse_whenIdIsValid() throws Exception {
		when(paymentService.getPaymentByBookingId(anyLong()))
				.thenReturn(createPaymentResponse());

		mockMvc.perform(
				       get("/api/v1/bookings/{id}/payment", bookingId)
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isOk())
		       .andExpect(jsonPath("$.paymentId").value(400L))
		       .andExpect(jsonPath("$.amount").value(totalAmount))
		       .andExpect(jsonPath("$.paymentMethod").value(PaymentMethod.CREDIT_CARD.getDisplayName()));
	}

	@Test
	public void getPaymentByBookingId_should_return400BadRequest_whenIdIsInvalid() throws Exception {
		mockMvc.perform(
				       get("/api/v1/bookings/{id}/payment", -1)
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isBadRequest())
		       .andExpect(jsonPath("$.statusCode").value(400))
		       .andExpect(jsonPath("$.path").value("/api/v1/bookings/-1/payment"));

		verify(paymentService, never()).getPaymentByBookingId(anyLong());
	}

	@Test
	public void getPaymentByBookingId_should_return404NotFound_whenBookingIsNotFound() throws Exception {
		doThrow(EntityNotFoundException.class).when(paymentService)
		                                      .getPaymentByBookingId(anyLong());

		mockMvc.perform(
				       get("/api/v1/bookings/{id}/payment", bookingId)
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isNotFound())
		       .andExpect(jsonPath("$.statusCode").value(404))
		       .andExpect(jsonPath("$.path").value("/api/v1/bookings/100/payment"));
	}

	@Test
	public void cancelBookingById_should_return204NoContent_whenIdIsValid() throws Exception {
		doNothing().when(bookingService).cancelBooking(anyLong());

		mockMvc.perform(
				patch("/api/v1/bookings/{id}/cancel", bookingId)
						.contentType(MediaType.APPLICATION_JSON)
		).andExpect(status().isNoContent());

		verify(bookingService).cancelBooking(anyLong());
	}

	@Test
	public void cancelBookingById_should_return400BadRequest_whenIdIsInvalid() throws Exception {
		mockMvc.perform(
				       patch("/api/v1/bookings/{id}/cancel", -1)
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isBadRequest())
		       .andExpect(jsonPath("$.statusCode").value(400))
		       .andExpect(jsonPath("$.path").value("/api/v1/bookings/-1/cancel"));

		verify(bookingService, never()).cancelBooking(anyLong());
	}

	@Test
	public void cancelBookingById_should_return404NotFound_whenBookingIsNotFound() throws Exception {
		doThrow(EntityNotFoundException.class).when(bookingService)
		                                      .cancelBooking(anyLong());

		mockMvc.perform(
				       patch("/api/v1/bookings/{id}/cancel", bookingId)
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isNotFound())
		       .andExpect(jsonPath("$.statusCode").value(404))
		       .andExpect(jsonPath("$.path").value("/api/v1/bookings/100/cancel"));
	}

	@Test
	public void cancelBookingById_should_return400BadRequest_whenBookingStatusIsNotPending() throws Exception {
		doThrow(InvalidOperationException.class).when(bookingService)
		                                        .cancelBooking(anyLong());

		mockMvc.perform(
				       patch("/api/v1/bookings/{id}/cancel", bookingId)
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isBadRequest())
		       .andExpect(jsonPath("$.statusCode").value(400))
		       .andExpect(jsonPath("$.path").value("/api/v1/bookings/100/cancel"));
	}

	@Test
	public void refundPayment_should_return204NoContent_whenIdIsValid() throws Exception {
		doNothing().when(paymentService).refundPayment(anyLong());

		mockMvc.perform(
				patch("/api/v1/bookings/{id}/refund", bookingId)
						.contentType(MediaType.APPLICATION_JSON)
		).andExpect(status().isNoContent());

		verify(paymentService).refundPayment(anyLong());
	}

	@Test
	public void refundPayment_should_return400BadRequest_whenIdIsInvalid() throws Exception {
		mockMvc.perform(
				       patch("/api/v1/bookings/{id}/refund", -1)
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isBadRequest())
		       .andExpect(jsonPath("$.statusCode").value(400))
		       .andExpect(jsonPath("$.path").value("/api/v1/bookings/-1/refund"));

		verify(paymentService, never()).refundPayment(anyLong());
	}

	@Test
	public void refundPayment_should_return404NotFound_whenPaymentOrBookingIsNotFound() throws Exception {
		doThrow(EntityNotFoundException.class).when(paymentService)
		                                      .refundPayment(anyLong());

		mockMvc.perform(
				       patch("/api/v1/bookings/{id}/refund", bookingId)
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isNotFound())
		       .andExpect(jsonPath("$.statusCode").value(404))
		       .andExpect(jsonPath("$.path").value("/api/v1/bookings/100/refund"));
	}

	@Test
	public void refundPayment_should_return400BadRequest_whenPaymentStatusIsNotCompleted() throws Exception {
		doThrow(InvalidOperationException.class).when(paymentService)
		                                        .refundPayment(anyLong());

		mockMvc.perform(
				       patch("/api/v1/bookings/{id}/refund", bookingId)
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
		                      .paidAt(null)
		                      .build();
	}
}
