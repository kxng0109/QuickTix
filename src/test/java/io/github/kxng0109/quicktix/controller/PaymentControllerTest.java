package io.github.kxng0109.quicktix.controller;

import io.github.kxng0109.quicktix.dto.request.PaymentRequest;
import io.github.kxng0109.quicktix.dto.response.PaymentResponse;
import io.github.kxng0109.quicktix.enums.PaymentMethod;
import io.github.kxng0109.quicktix.enums.PaymentStatus;
import io.github.kxng0109.quicktix.exception.InvalidAmountException;
import io.github.kxng0109.quicktix.exception.InvalidOperationException;
import io.github.kxng0109.quicktix.service.PaymentService;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
public class PaymentControllerTest {

	private final Long paymentId = 2L;
	private final String transactionReference = UUID.randomUUID().toString();
	private final BigDecimal amount = BigDecimal.valueOf(12345.68);
	private final PaymentMethod paymentMethod = PaymentMethod.DEBIT_CARD;
	private final PaymentStatus paymentStatus = PaymentStatus.COMPLETED;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private PaymentService paymentService;

	private PaymentRequest request;
	private PaymentResponse response;

	@BeforeEach
	public void setup() {
		Long bookingId = 1L;
		request = PaymentRequest.builder()
		                        .bookingId(bookingId)
		                        .amount(amount)
		                        .paymentMethod(paymentMethod)
		                        .build();

		response = PaymentResponse.builder()
		                          .paymentId(paymentId)
		                          .amount(amount)
		                          .status(paymentStatus.getDisplayName())
		                          .paymentMethod(paymentMethod.getDisplayName())
		                          .paidAt(Instant.now())
		                          .build();
	}

	@Test
	public void initializePayment_should_return201CreatedAndPaymentResponse_whenRequestIsValid() throws Exception {
		when(paymentService.initializePayment(any(PaymentRequest.class)))
				.thenReturn(response);

		mockMvc.perform(
				       post("/api/v1/payments/initialize")
						       .contentType(MediaType.APPLICATION_JSON)
						       .content(objectMapper.writeValueAsString(request))
		       ).andExpect(status().isCreated())
		       .andExpect(jsonPath("$.paymentId").value(paymentId))
		       .andExpect(jsonPath("$.amount").value(amount))
		       .andExpect(jsonPath("$.status").value(paymentStatus.getDisplayName()))
		       .andExpect(jsonPath("$.paymentMethod").value(paymentMethod.getDisplayName()));
	}

	@Test
	public void initializePayment_should_return400BadRequest_whenRequestIsInvalid() throws Exception {
		PaymentRequest badRequest = PaymentRequest.builder().build();
		mockMvc.perform(
				       post("/api/v1/payments/initialize")
						       .contentType(MediaType.APPLICATION_JSON)
						       .content(objectMapper.writeValueAsString(badRequest))
		       ).andExpect(status().isBadRequest())
		       .andExpect(jsonPath("$.bookingId").value("Booking ID is required"))
		       .andExpect(jsonPath("$.amount").value("Amount is required"))
		       .andExpect(jsonPath("$.paymentMethod").value("Payment method is required"));

		verify(paymentService, never()).initializePayment(any(PaymentRequest.class));
	}

	@Test
	public void initializePayment_should_return404NotFound_whenBookingIsNotFound() throws Exception {
		when(paymentService.initializePayment(any(PaymentRequest.class)))
				.thenThrow(EntityNotFoundException.class);

		mockMvc.perform(
				       post("/api/v1/payments/initialize")
						       .contentType(MediaType.APPLICATION_JSON)
						       .content(objectMapper.writeValueAsString(request))
		       ).andExpect(status().isNotFound())
		       .andExpect(jsonPath("$.statusCode").value(404))
		       .andExpect(jsonPath("$.path").value("/api/v1/payments/initialize"));
	}

	@Test
	public void initializePayment_should_return400BadRequest_whenPaymentStatusIsNotPending() throws Exception {
		when(paymentService.initializePayment(any(PaymentRequest.class)))
				.thenThrow(InvalidOperationException.class);

		mockMvc.perform(
				       post("/api/v1/payments/initialize")
						       .contentType(MediaType.APPLICATION_JSON)
						       .content(objectMapper.writeValueAsString(request))
		       ).andExpect(status().isBadRequest())
		       .andExpect(jsonPath("$.statusCode").value(400))
		       .andExpect(jsonPath("$.path").value("/api/v1/payments/initialize"));
	}

	@Test
	public void initializePayment_should_return400BadRequest_whenAmountIsDifferent() throws Exception {
		when(paymentService.initializePayment(any(PaymentRequest.class)))
				.thenThrow(InvalidAmountException.class);

		mockMvc.perform(
				       post("/api/v1/payments/initialize")
						       .contentType(MediaType.APPLICATION_JSON)
						       .content(objectMapper.writeValueAsString(request))
		       ).andExpect(status().isBadRequest())
		       .andExpect(jsonPath("$.statusCode").value(400))
		       .andExpect(jsonPath("$.path").value("/api/v1/payments/initialize"));
	}

	@Test
	public void verifyPayment_should_return200OkAndPaymentResponse_whenTransactionReferenceIsValid() throws Exception {
		when(paymentService.verifyPayment(anyString()))
				.thenReturn(response);

		mockMvc.perform(
				       get("/api/v1/payments/verify/{transactionReference}", transactionReference)
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isOk())
		       .andExpect(jsonPath("$.paymentId").value(paymentId))
		       .andExpect(jsonPath("$.amount").value(amount))
		       .andExpect(jsonPath("$.status").value(paymentStatus.getDisplayName()))
		       .andExpect(jsonPath("$.paymentMethod").value(paymentMethod.getDisplayName()));
	}

	@Test
	public void verifyPayment_should_return400BadRequest_whenTransactionReferenceIsInvalid() throws Exception {
		mockMvc.perform(
				       get("/api/v1/payments/verify/{transactionReference}", " ")
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isBadRequest())
		       .andExpect(jsonPath("$.statusCode").value(400))
		       .andExpect(jsonPath("$.path").value("/api/v1/payments/verify/%20"));

		verify(paymentService, never()).verifyPayment(anyString());
	}

	@Test
	public void verifyPayment_should_return404NotFound_whenBookingIsNotFound() throws Exception {
		when(paymentService.verifyPayment(anyString()))
				.thenThrow(EntityNotFoundException.class);

		mockMvc.perform(
				       get("/api/v1/payments/verify/{transactionReference}", transactionReference)
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isNotFound())
		       .andExpect(jsonPath("$.statusCode").value(404))
		       .andExpect(jsonPath("$.path").value("/api/v1/payments/verify/" + transactionReference));
	}
}
