package io.github.kxng0109.quicktix.controller;

import io.github.kxng0109.quicktix.dto.request.HoldSeatsRequest;
import io.github.kxng0109.quicktix.dto.response.SeatResponse;
import io.github.kxng0109.quicktix.enums.SeatStatus;
import io.github.kxng0109.quicktix.service.SeatService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SeatController.class)
public class SeatControllerTest {

	private final Long eventId = 100L;
	private final Long seatId = 200L;
	private final List<Long> seatIds = List.of(201L, 202L, 203L);
	private final HoldSeatsRequest badRequest = HoldSeatsRequest.builder().build();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private SeatService seatService;

	private HoldSeatsRequest request;
	private SeatResponse response;

	@BeforeEach
	public void setup() {
		request = HoldSeatsRequest.builder()
		                          .eventId(eventId)
		                          .userId(300L)
		                          .seatIds(seatIds)
		                          .build();

		response = SeatResponse.builder()
		                       .id(seatId)
		                       .seatNumber(23)
		                       .rowName("E")
		                       .status(SeatStatus.AVAILABLE.getDisplayName())
		                       .build();
	}

	@Test
	public void getAllSeatsByEvent_should_return200OkAndAPageOfSeatResponse_whenIdIsValid() throws Exception {
		Page<SeatResponse> seatResponsePage = new PageImpl<>(List.of(response));
		when(seatService.getAllSeatsByEvent(anyLong(), any(Pageable.class)))
				.thenReturn(seatResponsePage);

		mockMvc.perform(
				       get("/api/v1/events/{eventId}/seats", eventId)
						       .param("page", "0")
						       .param("size", "10")
						       .param("sort", "id,desc")
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isOk())
		       .andExpect(jsonPath("$.content").isArray())
		       .andExpect(jsonPath("$.content.length()").value(1))
		       .andExpect(jsonPath("$.size").value(1))
		       .andExpect(jsonPath("$.content[0].id").value(seatId));
	}

	@Test
	public void getAllSeatsByEvent_should_useDefaults_whenPageableParamsAreMissing() throws Exception {
		Page<SeatResponse> seatResponsePage = new PageImpl<>(List.of(response));
		when(seatService.getAllSeatsByEvent(anyLong(), any(Pageable.class)))
				.thenReturn(seatResponsePage);

		mockMvc.perform(
				       get("/api/v1/events/{eventId}/seats", eventId)
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isOk())
		       .andExpect(jsonPath("$.content").isArray())
		       .andExpect(jsonPath("$.content.length()").value(1))
		       .andExpect(jsonPath("$.size").value(1))
		       .andExpect(jsonPath("$.content[0].id").value(seatId));
	}

	@Test
	public void getAllSeatsByEvent_should_return400BadRequest_whenIdIsInvalid() throws Exception {
		mockMvc.perform(
				       get("/api/v1/events/{eventId}/seats", -1)
						       .param("page", "0")
						       .param("size", "10")
						       .param("sort", "id,desc")
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isBadRequest())
		       .andExpect(jsonPath("$.statusCode").value(400))
		       .andExpect(jsonPath("$.path").value("/api/v1/events/-1/seats"));

		verify(seatService, never()).getAllSeatsByEvent(anyLong(), any(Pageable.class));
	}

	@Test
	public void getAllSeatsByEvent_should_return404NotFound_whenEventIsNotFound() throws Exception {
		doThrow(EntityNotFoundException.class)
				.when(seatService).getAllSeatsByEvent(anyLong(), any(Pageable.class));

		mockMvc.perform(
				       get("/api/v1/events/{eventId}/seats", eventId)
						       .param("page", "0")
						       .param("size", "10")
						       .param("sort", "id,desc")
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isNotFound())
		       .andExpect(jsonPath("$.statusCode").value(404))
		       .andExpect(jsonPath("$.path").value("/api/v1/events/100/seats"));
	}

	@Test
	public void getAvailableSeats_should_return200OkAndAPageOfSeatResponse_whenIdIsValid() throws Exception {
		Page<SeatResponse> seatResponsePage = new PageImpl<>(List.of(response));
		when(seatService.getAvailableSeats(anyLong(), any(Pageable.class)))
				.thenReturn(seatResponsePage);

		mockMvc.perform(
				       get("/api/v1/events/{eventId}/seats/available", eventId)
						       .param("page", "0")
						       .param("size", "10")
						       .param("sort", "id,desc")
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isOk())
		       .andExpect(jsonPath("$.content").isArray())
		       .andExpect(jsonPath("$.content.length()").value(1))
		       .andExpect(jsonPath("$.size").value(1))
		       .andExpect(jsonPath("$.content[0].id").value(seatId));
	}

	@Test
	public void getAvailableSeats_should_useDefaults_whenPageableParamsAreMissing() throws Exception {
		Page<SeatResponse> seatResponsePage = new PageImpl<>(List.of(response));
		when(seatService.getAvailableSeats(anyLong(), any(Pageable.class)))
				.thenReturn(seatResponsePage);

		mockMvc.perform(
				       get("/api/v1/events/{eventId}/seats/available", eventId)
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isOk())
		       .andExpect(jsonPath("$.content").isArray())
		       .andExpect(jsonPath("$.content.length()").value(1))
		       .andExpect(jsonPath("$.size").value(1))
		       .andExpect(jsonPath("$.content[0].id").value(seatId));
	}

	@Test
	public void getAvailableSeats_should_return400BadRequest_whenIdIsInvalid() throws Exception {
		mockMvc.perform(
				       get("/api/v1/events/{eventId}/seats/available", -1)
						       .param("page", "0")
						       .param("size", "10")
						       .param("sort", "id,desc")
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isBadRequest())
		       .andExpect(jsonPath("$.statusCode").value(400))
		       .andExpect(jsonPath("$.path").value("/api/v1/events/-1/seats/available"));

		verify(seatService, never()).getAvailableSeats(anyLong(), any(Pageable.class));
	}

	@Test
	public void getAvailableSeats_should_return404NotFound_whenEventIsNotFound() throws Exception {
		doThrow(EntityNotFoundException.class)
				.when(seatService).getAvailableSeats(anyLong(), any(Pageable.class));

		mockMvc.perform(
				       get("/api/v1/events/{eventId}/seats/available", eventId)
						       .param("page", "0")
						       .param("size", "10")
						       .param("sort", "id,desc")
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isNotFound())
		       .andExpect(jsonPath("$.statusCode").value(404))
		       .andExpect(jsonPath("$.path").value("/api/v1/events/100/seats/available"));
	}

	@Test
	public void holdSeats_should_return201CreatedAndListOfSeatResponse_whenRequestIsValid() throws Exception {
		when(seatService.holdSeats(any(HoldSeatsRequest.class)))
				.thenReturn(List.of(response));

		mockMvc.perform(
				       post("/api/v1/seats/hold")
						       .contentType(MediaType.APPLICATION_JSON)
						       .content(objectMapper.writeValueAsString(request))
		       ).andExpect(status().isCreated())
		       .andExpect(jsonPath("$.length()").value(1))
		       .andExpect(jsonPath("$[0].id").value(seatId));
	}

	@Test
	public void holdSeats_should_return400BadRequest_whenRequestIsInvalid() throws Exception {
		mockMvc.perform(
				       post("/api/v1/seats/hold")
						       .contentType(MediaType.APPLICATION_JSON)
						       .content(objectMapper.writeValueAsString(badRequest))
		       ).andExpect(status().isBadRequest())
		       .andExpect(jsonPath("$.eventId").value("Event ID can't be null"))
		       .andExpect(jsonPath("$.userId").value("User ID can't be null"));

		verify(seatService, never()).holdSeats(any(HoldSeatsRequest.class));
	}

	@Test
	public void holdSeats_should_return400BadRequest_whenSeatAreNotAvailable() throws Exception {
		doThrow(IllegalArgumentException.class)
				.when(seatService).holdSeats(any(HoldSeatsRequest.class));

		mockMvc.perform(
				       post("/api/v1/seats/hold")
						       .contentType(MediaType.APPLICATION_JSON)
						       .content(objectMapper.writeValueAsString(request))
		       ).andExpect(status().isBadRequest())
		       .andExpect(jsonPath("$.statusCode").value(400))
		       .andExpect(jsonPath("$.path").value("/api/v1/seats/hold"));
	}

	@Test
	public void holdSeats_should_return404NotFound_whenEventOrUserIsNotFound() throws Exception {
		doThrow(EntityNotFoundException.class)
				.when(seatService).holdSeats(any(HoldSeatsRequest.class));

		mockMvc.perform(
				       post("/api/v1/seats/hold")
						       .contentType(MediaType.APPLICATION_JSON)
						       .content(objectMapper.writeValueAsString(request))
		       ).andExpect(status().isNotFound())
		       .andExpect(jsonPath("$.statusCode").value(404))
		       .andExpect(jsonPath("$.path").value("/api/v1/seats/hold"));
	}

	@Test
	public void releaseSeats_should_return204NoContent_whenRequestIsValid() throws Exception {
		mockMvc.perform(
				post("/api/v1/seats/release")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request))
		).andExpect(status().isNoContent());

		verify(seatService).releaseSeats(any(HoldSeatsRequest.class));
	}

	@Test
	public void releaseSeats_should_return400BadRequest_whenRequestIsInvalid() throws Exception {
		mockMvc.perform(
				       post("/api/v1/seats/release")
						       .contentType(MediaType.APPLICATION_JSON)
						       .content(objectMapper.writeValueAsString(badRequest))
		       ).andExpect(status().isBadRequest())
		       .andExpect(jsonPath("$.eventId").value("Event ID can't be null"))
		       .andExpect(jsonPath("$.userId").value("User ID can't be null"));

		verify(seatService, never()).releaseSeats(any(HoldSeatsRequest.class));
	}

	@Test
	public void releaseSeats_should_return400BadRequest_whenSeatAreNotAvailable() throws Exception {
		doThrow(IllegalArgumentException.class)
				.when(seatService).releaseSeats(any(HoldSeatsRequest.class));

		mockMvc.perform(
				       post("/api/v1/seats/release")
						       .contentType(MediaType.APPLICATION_JSON)
						       .content(objectMapper.writeValueAsString(request))
		       ).andExpect(status().isBadRequest())
		       .andExpect(jsonPath("$.statusCode").value(400))
		       .andExpect(jsonPath("$.path").value("/api/v1/seats/release"));
	}

	@Test
	public void releaseSeats_should_return404NotFound_whenEventOrUserIsNotFound() throws Exception {
		doThrow(EntityNotFoundException.class)
				.when(seatService).releaseSeats(any(HoldSeatsRequest.class));

		mockMvc.perform(
				       post("/api/v1/seats/release")
						       .contentType(MediaType.APPLICATION_JSON)
						       .content(objectMapper.writeValueAsString(request))
		       ).andExpect(status().isNotFound())
		       .andExpect(jsonPath("$.statusCode").value(404))
		       .andExpect(jsonPath("$.path").value("/api/v1/seats/release"));
	}
}
