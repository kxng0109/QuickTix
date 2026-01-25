package io.github.kxng0109.quicktix.controller;

import io.github.kxng0109.quicktix.dto.request.CreateEventRequest;
import io.github.kxng0109.quicktix.dto.request.EventDateSearchRequest;
import io.github.kxng0109.quicktix.dto.response.EventResponse;
import io.github.kxng0109.quicktix.dto.response.SeatResponse;
import io.github.kxng0109.quicktix.enums.EventStatus;
import io.github.kxng0109.quicktix.enums.SeatStatus;
import io.github.kxng0109.quicktix.service.EventService;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventController.class)
public class EventControllerTest {

	private final Long eventId = 100L;
	private final Long venueId = 200L;
	private final Long seatId = 300L;
	private final CreateEventRequest badRequest = CreateEventRequest.builder().build();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private EventService eventService;

	@MockitoBean
	private SeatService seatService;

	private CreateEventRequest request;
	private EventResponse response;

	@BeforeEach
	public void setup() {
		request = CreateEventRequest.builder()
		                            .name("event name")
		                            .description("event description")
		                            .venueId(venueId)
		                            .eventStartDateTime(Instant.now().plus(2, ChronoUnit.HOURS))
		                            .eventEndDateTime(Instant.now().plus(3, ChronoUnit.HOURS))
		                            .ticketPrice(BigDecimal.valueOf(12345.68))
		                            .numberOfSeats(40000)
		                            .build();

		response = EventResponse.builder()
		                        .id(eventId)
		                        .name(request.name())
		                        .description(request.description())
		                        .venueName("venue name")
		                        .ticketPrice(request.ticketPrice())
		                        .status(EventStatus.UPCOMING.getDisplayName())
		                        .availableSeats(30000)
		                        .eventStartDateTime(request.eventStartDateTime())
		                        .eventEndDateTime(request.eventEndDateTime())
		                        .build();
	}

	@Test
	public void createEvent_should_return201CreatedAndEventResponse_whenRequestIsValid() throws Exception {
		when(eventService.createEvent(any(CreateEventRequest.class)))
				.thenReturn(response);

		mockMvc.perform(
				       post("/api/v1/events")
						       .contentType(MediaType.APPLICATION_JSON)
						       .content(objectMapper.writeValueAsString(request))
		       ).andExpect(status().isCreated())
		       .andExpect(jsonPath("$.id").value(eventId))
		       .andExpect(jsonPath("$.name").value(request.name()))
		       .andExpect(jsonPath("$.description").value(request.description()))
		       .andExpect(jsonPath("$.venueName").value(response.venueName()));
	}

	@Test
	public void createEvent_should_return400BadRequest_whenRequestIsInvalid() throws Exception {
		String uriTemplate = "/api/v1/events";

		mockMvc.perform(
				       post(uriTemplate)
						       .contentType(MediaType.APPLICATION_JSON)
						       .content(objectMapper.writeValueAsString(badRequest))
		       ).andExpect(status().isBadRequest())
		       .andExpect(jsonPath("$.name").value("Event name can't be blank."))
		       .andExpect(jsonPath("$.description").value("Event description can't be blank."))
		       .andExpect(jsonPath("$.venueId").value("Event venue ID can't be blank."))
		       .andExpect(jsonPath("$.eventStartDateTime").value("Event start date time must not be blank"));

		verify(eventService, never()).createEvent(any(CreateEventRequest.class));
	}

	@Test
	public void createEvent_should_return404NotFound_whenVenueIsNotFound() throws Exception {
		String uriTemplate = "/api/v1/events";

		doThrow(EntityNotFoundException.class)
				.when(eventService)
				.createEvent(any(CreateEventRequest.class));

		mockMvc.perform(
				       post(uriTemplate)
						       .contentType(MediaType.APPLICATION_JSON)
						       .content(objectMapper.writeValueAsString(request))
		       ).andExpect(status().isNotFound())
		       .andExpect(jsonPath("$.statusCode").value(404))
		       .andExpect(jsonPath("$.path").value(uriTemplate));
	}

	@Test
	public void getEventById_should_return200OkAndEventResponse_whenIdIsValid() throws Exception {
		when(eventService.getEventById(anyLong()))
				.thenReturn(response);

		mockMvc.perform(
				       get("/api/v1/events/{id}", eventId)
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isOk())
		       .andExpect(jsonPath("$.id").value(eventId))
		       .andExpect(jsonPath("$.name").value(request.name()))
		       .andExpect(jsonPath("$.description").value(request.description()))
		       .andExpect(jsonPath("$.venueName").value(response.venueName()));
	}

	@Test
	public void getEventById_should_return400BadRequest_whenIdIsInvalid() throws Exception {
		mockMvc.perform(
				       get("/api/v1/events/{id}", -1L)
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isBadRequest())
		       .andExpect(jsonPath("$.statusCode").value(400))
		       .andExpect(jsonPath("$.path").value("/api/v1/events/-1"));

		verify(eventService, never()).getEventById(anyLong());
	}

	@Test
	public void getEventById_should_return404NotFound_whenEventIsNotFound() throws Exception {
		doThrow(EntityNotFoundException.class)
				.when(eventService).getEventById(anyLong());

		mockMvc.perform(
				       get("/api/v1/events/{id}", eventId)
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isNotFound())
		       .andExpect(jsonPath("$.statusCode").value(404))
		       .andExpect(jsonPath("$.path").value("/api/v1/events/100"));
	}

	@Test
	public void getAllUpcomingEvents_should_return200OkAndPageOfEventResponse_whenRequestIsSuccessful() throws Exception {
		Page<EventResponse> eventResponsePage = new PageImpl<>(List.of(response));
		when(eventService.getAllUpcomingEvents(any(Pageable.class)))
				.thenReturn(eventResponsePage);

		mockMvc.perform(
				       get("/api/v1/events/upcoming")
						       .param("page", "0")
						       .param("size", "10")
						       .param("sort", "id,desc")
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isOk())
		       .andExpect(jsonPath("$.content").isArray())
		       .andExpect(jsonPath("$.content.length()").value(1))
		       .andExpect(jsonPath("$.content[0].id").value(eventId))
		       .andExpect(jsonPath("$.numberOfElements").value(1))
		       .andExpect(jsonPath("$.size").value(1))
		       .andExpect(jsonPath("$.totalElements").value(1))
		       .andExpect(jsonPath("$.totalPages").value(1));
	}

	@Test
	public void getAllUpcomingEvents_should_useDefaults_whenParamsAreMissing() throws Exception {
		Page<EventResponse> eventResponsePage = new PageImpl<>(List.of(response));
		when(eventService.getAllUpcomingEvents(any(Pageable.class)))
				.thenReturn(eventResponsePage);

		mockMvc.perform(
				       get("/api/v1/events/upcoming")
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isOk())
		       .andExpect(jsonPath("$.content").isArray())
		       .andExpect(jsonPath("$.content.length()").value(1))
		       .andExpect(jsonPath("$.content[0].id").value(eventId))
		       .andExpect(jsonPath("$.numberOfElements").value(1))
		       .andExpect(jsonPath("$.size").value(1))
		       .andExpect(jsonPath("$.totalElements").value(1))
		       .andExpect(jsonPath("$.totalPages").value(1));
	}

	@Test
	public void getAllSeatsByEvent_should_return200OkAndAPageOfSeatResponse_whenIdIsValid() throws Exception {
		Page<SeatResponse> seatResponsePage = new PageImpl<>(List.of(seatResponse()));
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
		Page<SeatResponse> seatResponsePage = new PageImpl<>(List.of(seatResponse()));
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
		Page<SeatResponse> seatResponsePage = new PageImpl<>(List.of(seatResponse()));
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
		Page<SeatResponse> seatResponsePage = new PageImpl<>(List.of(seatResponse()));
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
	public void getEventsByVenueId_should_return200OkAndPageOfEventResponse_whenRequestIsSuccessful() throws Exception {
		Page<EventResponse> eventResponsePage = new PageImpl<>(List.of(response));
		when(eventService.getEventsByVenueId(anyLong(), any(Pageable.class)))
				.thenReturn(eventResponsePage);

		mockMvc.perform(
				       get("/api/v1/events/venue/{venueId}", venueId)
						       .param("page", "0")
						       .param("size", "10")
						       .param("sort", "id,desc")
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isOk())
		       .andExpect(jsonPath("$.content").isArray())
		       .andExpect(jsonPath("$.content.length()").value(1))
		       .andExpect(jsonPath("$.content[0].id").value(eventId))
		       .andExpect(jsonPath("$.numberOfElements").value(1))
		       .andExpect(jsonPath("$.size").value(1))
		       .andExpect(jsonPath("$.totalElements").value(1))
		       .andExpect(jsonPath("$.totalPages").value(1));
	}

	@Test
	public void getEventsByVenueId_should_useDefaults_whenPageableParamsAreMissing() throws Exception {
		Page<EventResponse> eventResponsePage = new PageImpl<>(List.of(response));
		when(eventService.getEventsByVenueId(anyLong(), any(Pageable.class)))
				.thenReturn(eventResponsePage);

		mockMvc.perform(
				       get("/api/v1/events/venue/{venueId}", venueId)
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isOk())
		       .andExpect(jsonPath("$.content").isArray())
		       .andExpect(jsonPath("$.content.length()").value(1))
		       .andExpect(jsonPath("$.content[0].id").value(eventId))
		       .andExpect(jsonPath("$.numberOfElements").value(1))
		       .andExpect(jsonPath("$.size").value(1))
		       .andExpect(jsonPath("$.totalElements").value(1))
		       .andExpect(jsonPath("$.totalPages").value(1));
	}

	@Test
	public void getEventsByVenueId_should_return400BadRequest_whenIdIsNotValid() throws Exception {
		mockMvc.perform(
				       get("/api/v1/events/venue/{venueId}", -1)
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isBadRequest())
		       .andExpect(jsonPath("$.statusCode").value(400))
		       .andExpect(jsonPath("$.path").value("/api/v1/events/venue/-1"));

		verify(eventService, never()).getEventsByVenueId(anyLong(), any(Pageable.class));
	}

	@Test
	public void getEventsByVenueId_should_return404NotFound_whenVenueIsNotFound() throws Exception {
		doThrow(EntityNotFoundException.class)
				.when(eventService).getEventsByVenueId(anyLong(), any(Pageable.class));

		mockMvc.perform(
				       get("/api/v1/events/venue/{venueId}", venueId)
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isNotFound())
		       .andExpect(jsonPath("$.statusCode").value(404))
		       .andExpect(jsonPath("$.path").value("/api/v1/events/venue/" + venueId));
	}

	@Test
	public void getEventsByDateRange_should_return200OkAndPageOfEventResponse_whenRequestIsSuccessful() throws Exception {
		Page<EventResponse> eventResponsePage = new PageImpl<>(List.of(response));
		when(eventService.getEventsByDateRange(any(EventDateSearchRequest.class), any(Pageable.class)))
				.thenReturn(eventResponsePage);

		mockMvc.perform(
				       get("/api/v1/events/date-range")
						       .param("startDate", "2099-01-01T00:00:00Z")
						       .param("endDate", "2099-02-01T00:00:00Z")
						       .param("page", "0")
						       .param("size", "10")
						       .param("sort", "id,desc")
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isOk())
		       .andExpect(jsonPath("$.content").isArray())
		       .andExpect(jsonPath("$.content.length()").value(1))
		       .andExpect(jsonPath("$.content[0].id").value(eventId))
		       .andExpect(jsonPath("$.numberOfElements").value(1))
		       .andExpect(jsonPath("$.size").value(1))
		       .andExpect(jsonPath("$.totalElements").value(1))
		       .andExpect(jsonPath("$.totalPages").value(1));
	}

	@Test
	public void getEventsByDateRange_should_useDefaults_whenPageableParamsAreMissing() throws Exception {
		Page<EventResponse> eventResponsePage = new PageImpl<>(List.of(response));
		when(eventService.getEventsByDateRange(any(EventDateSearchRequest.class), any(Pageable.class)))
				.thenReturn(eventResponsePage);

		mockMvc.perform(
				       get("/api/v1/events/date-range")
						       .param("startDate", "2099-01-01T00:00:00Z")
						       .param("endDate", "2099-02-01T00:00:00Z")
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isOk())
		       .andExpect(jsonPath("$.content").isArray())
		       .andExpect(jsonPath("$.content.length()").value(1))
		       .andExpect(jsonPath("$.content[0].id").value(eventId))
		       .andExpect(jsonPath("$.numberOfElements").value(1))
		       .andExpect(jsonPath("$.size").value(1))
		       .andExpect(jsonPath("$.totalElements").value(1))
		       .andExpect(jsonPath("$.totalPages").value(1));
	}

	@Test
	public void getEventsByDateRange_should_return400BadRequest_whenDateRangeIsInvalid() throws Exception {
		mockMvc.perform(
				       get("/api/v1/events/date-range")
						       .param("startDate", "2099-01-01T00:00:00Z")
						       .param("endDate", "2025-02-01T00:00:00Z")
						       .param("page", "0")
						       .param("size", "10")
						       .param("sort", "id,desc")
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isBadRequest())
		       .andExpect(jsonPath("$.dateRangeValid").value("Start date must be before end date"));

		verify(eventService, never()).getEventsByDateRange(any(EventDateSearchRequest.class), any(Pageable.class));
	}

	@Test
	public void updateEventById_should_return200OkAndEventResponse_whenRequestIsValid() throws Exception {
		when(eventService.updateEventById(anyLong(), any(CreateEventRequest.class)))
				.thenReturn(response);

		mockMvc.perform(
				       put("/api/v1/events/{id}", eventId)
						       .contentType(MediaType.APPLICATION_JSON)
						       .content(objectMapper.writeValueAsString(request))
		       ).andExpect(status().isOk())
		       .andExpect(jsonPath("$.id").value(eventId))
		       .andExpect(jsonPath("$.status").value(response.status()))
		       .andExpect(jsonPath("$.name").value(request.name()));
	}

	@Test
	public void updateEventById_should_return400BadRequest_whenRequestIsInvalid() throws Exception {
		mockMvc.perform(
				       put("/api/v1/events/{id}", eventId)
						       .contentType(MediaType.APPLICATION_JSON)
						       .content(objectMapper.writeValueAsString(badRequest))
		       ).andExpect(status().isBadRequest())
		       .andExpect(jsonPath("$.name").value("Event name can't be blank."))
		       .andExpect(jsonPath("$.description").value("Event description can't be blank."))
		       .andExpect(jsonPath("$.venueId").value("Event venue ID can't be blank."))
		       .andExpect(jsonPath("$.eventStartDateTime").value("Event start date time must not be blank"));

		verify(eventService, never()).updateEventById(anyLong(), any(CreateEventRequest.class));
	}

	@Test
	public void updateEventById_should_return400BadRequest_whenIdIsInvalid() throws Exception {
		mockMvc.perform(
				       put("/api/v1/events/{id}", -1)
						       .contentType(MediaType.APPLICATION_JSON)
						       .content(objectMapper.writeValueAsString(request))
		       ).andExpect(status().isBadRequest())
		       .andExpect(jsonPath("$.statusCode").value(400))
		       .andExpect(jsonPath("$.path").value("/api/v1/events/" + "-1"));

		verify(eventService, never()).updateEventById(anyLong(), any(CreateEventRequest.class));
	}

	@Test
	public void updateEventById_should_return404NotFound_whenEventIsNotFound() throws Exception {
		doThrow(EntityNotFoundException.class)
				.when(eventService).updateEventById(anyLong(), any(CreateEventRequest.class));

		mockMvc.perform(
				       put("/api/v1/events/", eventId)
						       .contentType(MediaType.APPLICATION_JSON)
						       .content(objectMapper.writeValueAsString(request))
		       ).andExpect(status().isNotFound())
		       .andExpect(jsonPath("$.statusCode").value(404))
		       .andExpect(jsonPath("$.path").value("/api/v1/events/"));
	}

	private SeatResponse seatResponse() {
		return SeatResponse.builder()
		                   .id(seatId)
		                   .seatNumber(23)
		                   .rowName("E")
		                   .status(SeatStatus.AVAILABLE.getDisplayName())
		                   .build();
	}
}
