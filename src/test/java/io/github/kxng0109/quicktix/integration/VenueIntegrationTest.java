package io.github.kxng0109.quicktix.integration;

import io.github.kxng0109.quicktix.dto.request.CreateVenueRequest;
import io.github.kxng0109.quicktix.entity.Venue;
import io.github.kxng0109.quicktix.repositories.VenueRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class VenueIntegrationTest extends BaseIntegrationTest {

	@Autowired
	private VenueRepository venueRepository;

	@Test
	void createVenue_shouldPersistVenueInDatabase() throws Exception {
		CreateVenueRequest request = CreateVenueRequest.builder()
		                                               .name("Grand Arena")
		                                               .address("123 Main Street")
		                                               .city("Lagos")
		                                               .totalCapacity(50000)
		                                               .build();

		mockMvc.perform(post("/api/v1/venues")
				                .contentType(MediaType.APPLICATION_JSON)
				                .content(objectMapper.writeValueAsString(request)))
		       .andExpect(status().isCreated())
		       .andExpect(jsonPath("$.id").exists())
		       .andExpect(jsonPath("$.name").value("Grand Arena"))
		       .andExpect(jsonPath("$.totalCapacity").value(50000));

		assertThat(venueRepository.findAll()).hasSize(1);
		Venue savedVenue = venueRepository.findAll().getFirst();
		assertThat(savedVenue.getName()).isEqualTo("Grand Arena");
		assertThat(savedVenue.getCity()).isEqualTo("Lagos");
	}

	@Test
	void getAllVenues_shouldReturnPagedResults() throws Exception {
		for (int i = 1; i <= 5; i++) {
			Venue venue = Venue.builder()
			                   .name("Venue " + i)
			                   .address("Address " + i)
			                   .city("City")
			                   .totalCapacity(1000 * i)
			                   .build();
			venueRepository.save(venue);
		}

		mockMvc.perform(get("/api/v1/venues")
				                .param("page", "0")
				                .param("size", "2"))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("$.content").isArray())
		       .andExpect(jsonPath("$.content.length()").value(2))
		       .andExpect(jsonPath("$.totalElements").value(5))
		       .andExpect(jsonPath("$.totalPages").value(3));
	}

	@Test
	void getVenuesByCity_shouldFilterCorrectly() throws Exception {
		venueRepository.save(Venue.builder()
		                          .name("Lagos Venue 1").address("Addr").city("Lagos").totalCapacity(1000).build());
		venueRepository.save(Venue.builder()
		                          .name("Lagos Venue 2").address("Addr").city("Lagos").totalCapacity(2000).build());
		venueRepository.save(Venue.builder()
		                          .name("Abuja Venue").address("Addr").city("Abuja").totalCapacity(3000).build());

		mockMvc.perform(get("/api/v1/venues/city/{city}", "Lagos"))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("$.content.length()").value(2))
		       .andExpect(jsonPath("$.totalElements").value(2));
	}
}