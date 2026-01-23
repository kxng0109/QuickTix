package io.github.kxng0109.quicktix.controller;

import io.github.kxng0109.quicktix.dto.request.CreateVenueRequest;
import io.github.kxng0109.quicktix.dto.response.VenueResponse;
import io.github.kxng0109.quicktix.entity.Venue;
import io.github.kxng0109.quicktix.exception.ResourceInUseException;
import io.github.kxng0109.quicktix.service.VenueService;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VenueController.class)
public class VenueControllerTest {

	private final Long venueId = 100L;
	private final CreateVenueRequest badRequest = CreateVenueRequest.builder().build();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private VenueService venueService;

	private CreateVenueRequest request;
	private VenueResponse response;

	@BeforeEach
	public void setup() {
		Venue venue = Venue.builder()
		                   .id(venueId)
		                   .name("Lol")
		                   .address("An address")
		                   .city("A city")
		                   .totalCapacity(30000)
		                   .build();

		request = CreateVenueRequest.builder()
		                            .name(venue.getName())
		                            .address(venue.getAddress())
		                            .city(venue.getCity())
		                            .totalCapacity(venue.getTotalCapacity())
		                            .build();

		response = VenueResponse.builder()
		                        .id(venueId)
		                        .name(venue.getName())
		                        .address(venue.getAddress())
		                        .city(venue.getCity())
		                        .totalCapacity(venue.getTotalCapacity())
		                        .build();
	}

	@Test
	public void createVenue_should_return201CreatedAndVenueResponse_whenRequestIsValid() throws Exception {
		when(venueService.createVenue(any(CreateVenueRequest.class)))
				.thenReturn(response);

		mockMvc.perform(
				       post("/api/v1/venues")
						       .contentType(MediaType.APPLICATION_JSON)
						       .content(objectMapper.writeValueAsString(request))
		       ).andExpect(status().isCreated())
		       .andExpect(jsonPath("$.id").value(venueId))
		       .andExpect(jsonPath("$.name").value(request.name()))
		       .andExpect(jsonPath("$.totalCapacity").value(request.totalCapacity()));
	}

	@Test
	public void createVenue_should_return400BadRequest_whenRequestIsInvalid() throws Exception {
		mockMvc.perform(
				       post("/api/v1/venues")
						       .contentType(MediaType.APPLICATION_JSON)
						       .content(objectMapper.writeValueAsString(badRequest))
		       ).andExpect(status().isBadRequest())
		       .andExpect(jsonPath("$.name").value("Venue name can't be blank"))
		       .andExpect(jsonPath("$.totalCapacity").value("Venue total capacity can not be blank"));

		verify(venueService, never()).createVenue(any(CreateVenueRequest.class));
	}

	@Test
	public void getVenueById_should_return200OkAndVenueResponse_whenRequestIsValid() throws Exception {
		when(venueService.getVenueById(anyLong()))
				.thenReturn(response);

		mockMvc.perform(
				       get("/api/v1/venues/" + venueId)
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isOk())
		       .andExpect(jsonPath("$.id").value(venueId))
		       .andExpect(jsonPath("$.name").value(request.name()))
		       .andExpect(jsonPath("$.totalCapacity").value(request.totalCapacity()));
	}

	@Test
	public void getVenueById_should_return400BadRequest_whenIdIsInvalid() throws Exception {
		String uriTemplate = "/api/v1/venues/" + -1;

		mockMvc.perform(
				       get(uriTemplate)
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isBadRequest())
		       .andExpect(jsonPath("$.statusCode").value(400))
		       .andExpect(jsonPath("$.path").value(uriTemplate));

		verify(venueService, never()).getVenueById(anyLong());
	}

	@Test
	public void getVenueById_should_return404NotFound_whenVenueIsNotFound() throws Exception {
		String uriTemplate = "/api/v1/venues/" + venueId;

		when(venueService.getVenueById(anyLong()))
				.thenThrow(EntityNotFoundException.class);

		mockMvc.perform(
				       get(uriTemplate)
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isNotFound())
		       .andExpect(jsonPath("$.statusCode").value(404))
		       .andExpect(jsonPath("$.path").value(uriTemplate));
	}

	@Test
	public void getAllVenue_should_return200OkAndPageOfVenueResponse_whenRequestIsValid() throws Exception {
		Page<VenueResponse> venueResponsePage = new PageImpl<>(List.of(response));

		when(venueService.getAllVenues(any(Pageable.class)))
				.thenReturn(venueResponsePage);

		mockMvc.perform(
				       get("/api/v1/venues")
						       .param("page", "0")
						       .param("size", "10")
						       .param("sort", "name,asc")
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isOk())
		       .andExpect(jsonPath("$.content.length()").value(1))
		       .andExpect(jsonPath("$.content[0].id").value(venueId))
		       .andExpect(jsonPath("$.content[0].name").value(request.name()))
		       .andExpect(jsonPath("$.totalElements").value(1))
		       .andExpect(jsonPath("$.size").value(1));
	}

	@Test
	public void getAllVenue_should_useDefaultsAnd_whenParamsAreMissing() throws Exception {
		Page<VenueResponse> venueResponsePage = new PageImpl<>(List.of(response));
		when(venueService.getAllVenues(any(Pageable.class)))
				.thenReturn(venueResponsePage);

		mockMvc.perform(
				       get("/api/v1/venues")
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isOk())
		       .andExpect(jsonPath("$.content.length()").value(1))
		       .andExpect(jsonPath("$.content[0].id").value(venueId));
	}

	@Test
	public void getVenuesByCity_should_return200OkAndPageOfVenueResponse_whenRequestIsValid() throws Exception {
		Page<VenueResponse> venueResponsePage = new PageImpl<>(List.of(response));

		when(venueService.getVenuesByCity(any(String.class), any(Pageable.class)))
				.thenReturn(venueResponsePage);

		mockMvc.perform(
				       get("/api/v1/venues/city/" + "lagos")
						       .param("page", "0")
						       .param("size", "10")
						       .param("sort", "name,asc")
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isOk())
		       .andExpect(jsonPath("$.content.length()").value(1))
		       .andExpect(jsonPath("$.content[0].id").value(venueId))
		       .andExpect(jsonPath("$.content[0].name").value(request.name()));
	}

	@Test
	public void getVenuesByCity_should_useDefaultsAnd_whenPageableParamsAreMissing() throws Exception {
		String uriTemplate = "/api/v1/venues/city/" + "lagos";

		Page<VenueResponse> venueResponsePage = new PageImpl<>(List.of(response));

		when(venueService.getVenuesByCity(any(String.class), any(Pageable.class)))
				.thenReturn(venueResponsePage);

		mockMvc.perform(
				       get(uriTemplate)
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isOk())
		       .andExpect(jsonPath("$.content.length()").value(1))
		       .andExpect(jsonPath("$.content[0].id").value(venueId))
		       .andExpect(jsonPath("$.content[0].name").value(request.name()));
	}

	@Test
	public void getVenuesByCity_should_return404NotFound_whenVenueIsNotFound() throws Exception {
		String uriTemplate = "/api/v1/venues/city/" + "lagos";

		doThrow(EntityNotFoundException.class)
				.when(venueService)
				.getVenuesByCity(any(String.class), any(Pageable.class));

		mockMvc.perform(
				       get(uriTemplate)
						       .param("page", "0")
						       .param("size", "10")
						       .param("sort", "name,asc")
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isNotFound())
		       .andExpect(jsonPath("$.statusCode").value(404))
		       .andExpect(jsonPath("$.path").value(uriTemplate));
	}

	@Test
	public void updateVenueById_should_return200OkAndVenueResponse_whenRequestIsValid() throws Exception {
		when(venueService.updateVenueById(anyLong(), any(CreateVenueRequest.class)))
				.thenReturn(response);

		mockMvc.perform(
				       put("/api/v1/venues/" + venueId)
						       .contentType(MediaType.APPLICATION_JSON)
						       .content(objectMapper.writeValueAsString(request))
		       ).andExpect(status().isOk())
		       .andExpect(jsonPath("$.id").value(venueId))
		       .andExpect(jsonPath("$.name").value(request.name()))
		       .andExpect(jsonPath("$.totalCapacity").value(request.totalCapacity()));
	}

	@Test
	public void updateVenueById_should_return400BadRequest_whenIdIsInvalid() throws Exception {
		String uriTemplate = "/api/v1/venues/" + -1;

		mockMvc.perform(
				       put(uriTemplate)
						       .contentType(MediaType.APPLICATION_JSON)
						       .content(objectMapper.writeValueAsString(request))
		       ).andExpect(status().isBadRequest())
		       .andExpect(jsonPath("$.statusCode").value(400))
		       .andExpect(jsonPath("$.path").value(uriTemplate));

		verify(venueService, never()).updateVenueById(anyLong(), any(CreateVenueRequest.class));
	}

	@Test
	public void updateVenueById_should_return400BadRequest_whenRequestIsInvalid() throws Exception {
		String uriTemplate = "/api/v1/venues/" + venueId;

		mockMvc.perform(
				       put(uriTemplate)
						       .contentType(MediaType.APPLICATION_JSON)
						       .content(objectMapper.writeValueAsString(badRequest))
		       ).andExpect(status().isBadRequest())
		       .andExpect(jsonPath("$.name").value("Venue name can't be blank"))
		       .andExpect(jsonPath("$.totalCapacity").value("Venue total capacity can not be blank"));

		verify(venueService, never()).updateVenueById(anyLong(), any(CreateVenueRequest.class));
	}

	@Test
	public void updateVenueById_should_return404NotFound_whenVenueIsNotFound() throws Exception {
		String uriTemplate = "/api/v1/venues/" + venueId;

		doThrow(EntityNotFoundException.class)
				.when(venueService)
				.updateVenueById(anyLong(), any(CreateVenueRequest.class));

		mockMvc.perform(
				       put(uriTemplate)
						       .contentType(MediaType.APPLICATION_JSON)
						       .content(objectMapper.writeValueAsString(request))
		       ).andExpect(status().isNotFound())
		       .andExpect(jsonPath("$.statusCode").value(404))
		       .andExpect(jsonPath("$.path").value(uriTemplate));
	}

	@Test
	public void deleteVenueById_should_return204NoContent_whenIdIsValid() throws Exception {
		doNothing().when(venueService).deleteVenueById(anyLong());

		mockMvc.perform(
				delete("/api/v1/venues/" + venueId)
		).andExpect(status().isNoContent());

		verify(venueService).deleteVenueById(anyLong());
	}

	@Test
	public void deleteVenueById_should_return400BadRequest_whenIdIsInvalid() throws Exception {
		String uriTemplate = "/api/v1/venues/" + -1;

		mockMvc.perform(
				       delete(uriTemplate)
		       ).andExpect(status().isBadRequest())
		       .andExpect(jsonPath("$.statusCode").value(400))
		       .andExpect(jsonPath("$.path").value(uriTemplate));

		verify(venueService, never()).deleteVenueById(anyLong());
	}

	@Test
	public void deleteVenueById_should_return404NotFound_whenVenueIsNotFound() throws Exception {
		String uriTemplate = "/api/v1/venues/" + venueId;

		doThrow(EntityNotFoundException.class)
				.when(venueService).deleteVenueById(anyLong());

		mockMvc.perform(
				       delete(uriTemplate)
		       ).andExpect(status().isNotFound())
		       .andExpect(jsonPath("$.statusCode").value(404))
		       .andExpect(jsonPath("$.path").value(uriTemplate));
	}

	@Test
	public void deleteVenueById_should_return409Conflict_whenVenueHasEvents() throws Exception {
		String uriTemplate = "/api/v1/venues/" + venueId;

		doThrow(ResourceInUseException.class)
				.when(venueService).deleteVenueById(anyLong());

		mockMvc.perform(
				       delete(uriTemplate)
		       ).andExpect(status().isConflict())
		       .andExpect(jsonPath("$.statusCode").value(409))
		       .andExpect(jsonPath("$.path").value(uriTemplate));

		verify(venueService).deleteVenueById(anyLong());
	}
}
