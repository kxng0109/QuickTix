package io.github.kxng0109.quicktix.integration;

import io.github.kxng0109.quicktix.dto.request.CreateUserRequest;
import io.github.kxng0109.quicktix.entity.User;
import io.github.kxng0109.quicktix.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for User operations.
 * <p>
 * These tests verify the complete flow from HTTP request to database and back.
 * Unlike unit tests, nothing is mocked here, we're testing real behavior.
 */
public class UserIntegrationTest extends BaseIntegrationTest {
	@Autowired
	private UserRepository userRepository;

	@Test
	void createUser_shouldPersistUserInDatabase_whenRequestIsValid() throws Exception {
		CreateUserRequest request = CreateUserRequest.builder()
		                                             .firstName("John")
		                                             .lastName("Doe")
		                                             .email("john.doe@example.com")
		                                             .phoneNumber("+2341234567890")
		                                             .build();

		mockMvc.perform(post("/api/v1/users")
				                .contentType(MediaType.APPLICATION_JSON)
				                .content(objectMapper.writeValueAsString(request)))
		       .andExpect(status().isCreated())
		       .andExpect(jsonPath("$.id").exists())
		       .andExpect(jsonPath("$.firstName").value("John"))
		       .andExpect(jsonPath("$.lastName").value("Doe"))
		       .andExpect(jsonPath("$.email").value("john.doe@example.com"));

		User savedUser = userRepository.findByEmail("john.doe@example.com").orElse(null);
		assertThat(savedUser).isNotNull();
		assertThat(savedUser.getFirstName()).isEqualTo("John");
		assertThat(savedUser.getLastName()).isEqualTo("Doe");
	}

	@Test
	void createUser_shouldReturnConflict_whenEmailAlreadyExists() throws Exception {
		User existingUser = User.builder()
		                        .firstName("Existing")
		                        .lastName("User")
		                        .email("existing@example.com")
		                        .build();
		userRepository.save(existingUser);

		CreateUserRequest request = CreateUserRequest.builder()
		                                             .firstName("Another")
		                                             .lastName("Person")
		                                             .email("existing@example.com")
		                                             .build();

		mockMvc.perform(post("/api/v1/users")
				                .contentType(MediaType.APPLICATION_JSON)
				                .content(objectMapper.writeValueAsString(request)))
		       .andExpect(status().isConflict());

		long count = userRepository.findAll().stream()
		                           .filter(u -> u.getEmail().equals("existing@example.com"))
		                           .count();
		assertThat(count).isEqualTo(1);
	}

	@Test
	void getUserById_shouldReturnUser_whenUserExists() throws Exception {
		User user = User.builder()
		                .firstName("Jane")
		                .lastName("Smith")
		                .email("jane.smith@example.com")
		                .build();
		User savedUser = userRepository.save(user);

		mockMvc.perform(get("/api/v1/users/{id}", savedUser.getId()))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("$.id").value(savedUser.getId()))
		       .andExpect(jsonPath("$.firstName").value("Jane"))
		       .andExpect(jsonPath("$.email").value("jane.smith@example.com"));
	}

	@Test
	void getUserById_shouldReturn404_whenUserDoesNotExist() throws Exception {
		mockMvc.perform(get("/api/v1/users/{id}", 99999))
		       .andExpect(status().isNotFound());
	}

	@Test
	void updateUser_shouldModifyUserInDatabase_whenRequestIsValid() throws Exception {
		User user = User.builder()
		                .firstName("Original")
		                .lastName("Name")
		                .email("original@example.com")
		                .build();
		User savedUser = userRepository.save(user);

		CreateUserRequest updateRequest = CreateUserRequest.builder()
		                                                   .firstName("Updated")
		                                                   .lastName("Name")
		                                                   .email("updated@example.com")
		                                                   .build();

		mockMvc.perform(put("/api/v1/users/{id}", savedUser.getId())
				                .contentType(MediaType.APPLICATION_JSON)
				                .content(objectMapper.writeValueAsString(updateRequest)))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("$.firstName").value("Updated"))
		       .andExpect(jsonPath("$.email").value("updated@example.com"));

		// Assert database was updated
		User updatedUser = userRepository.findById(savedUser.getId()).orElse(null);
		assertThat(updatedUser).isNotNull();
		assertThat(updatedUser.getFirstName()).isEqualTo("Updated");
		assertThat(updatedUser.getEmail()).isEqualTo("updated@example.com");
	}

	@Test
	void deleteUser_shouldRemoveUserFromDatabase_whenUserHasNoBookings() throws Exception {
		User user = User.builder()
		                .firstName("ToDelete")
		                .lastName("User")
		                .email("delete.me@example.com")
		                .build();
		userRepository.save(user);

		assertThat(userRepository.findByEmail("delete.me@example.com")).isPresent();

		mockMvc.perform(delete("/api/v1/users/email/{email}", "delete.me@example.com"))
		       .andExpect(status().isNoContent());

		assertThat(userRepository.findByEmail("delete.me@example.com")).isEmpty();
	}
}
