package io.github.kxng0109.quicktix.integration;

import io.github.kxng0109.quicktix.dto.request.CreateUserRequest;
import io.github.kxng0109.quicktix.entity.User;
import io.github.kxng0109.quicktix.enums.Role;
import io.github.kxng0109.quicktix.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class UserIntegrationTest extends BaseIntegrationTest {

	@Autowired
	private UserRepository userRepository;

	@Test
	void getUserById_shouldReturnUser_whenUserExists() throws Exception {
		User user = User.builder()
		                .firstName("Jane")
		                .lastName("Smith")
		                .email("jane.smith@example.com")
		                .passwordHash("password123")
		                .build();
		User savedUser = userRepository.save(user);

		mockMvc.perform(get("/api/v1/users/{id}", savedUser.getId())
				                .with(user("admin@test.com").roles("ADMIN")))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("$.id").value(savedUser.getId()))
		       .andExpect(jsonPath("$.firstName").value("Jane"))
		       .andExpect(jsonPath("$.email").value("jane.smith@example.com"));
	}

	@Test
	void getUserById_shouldReturn404_whenUserDoesNotExist() throws Exception {
		mockMvc.perform(get("/api/v1/users/{id}", 99999L)
				                .with(user("admin@test.com").roles("ADMIN")))
		       .andExpect(status().isNotFound());
	}

	@Test
	void updateUserById_shouldModifyUserInDatabase_whenRequestIsValid() throws Exception {
		User admin = userRepository.save(User.builder()
		                                     .firstName("Admin")
		                                     .lastName("User")
		                                     .email("admin@test.com")
		                                     .passwordHash("password123")
		                                     .role(Role.ADMIN)
		                                     .build());

		User targetUser = userRepository.save(User.builder()
		                                          .firstName("Original")
		                                          .lastName("Name")
		                                          .email("original@example.com")
		                                          .passwordHash("password123")
		                                          .build());

		CreateUserRequest updateRequest = CreateUserRequest.builder()
		                                                   .firstName("Updated")
		                                                   .lastName("Name")
		                                                   .email("updated@example.com")
		                                                   .password("password123")
		                                                   .phoneNumber("+2345555555555")
		                                                   .build();

		// Fix: pass the saved entity directly instead of .with(user(email).roles(...))
		// so that @AuthenticationPrincipal resolves to your custom User entity, not null
		mockMvc.perform(put("/api/v1/users/{id}", targetUser.getId())
				                .with(user(admin))
				                .contentType(MediaType.APPLICATION_JSON)
				                .content(objectMapper.writeValueAsString(updateRequest)))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("$.firstName").value("Updated"))
		       .andExpect(jsonPath("$.email").value("updated@example.com"));

		User updatedUser = userRepository.findById(targetUser.getId()).orElseThrow();
		assertThat(updatedUser.getFirstName()).isEqualTo("Updated");
		assertThat(updatedUser.getEmail()).isEqualTo("updated@example.com");
	}

	@Test
	void deleteUserMe_shouldRemoveUserFromDatabase_whenUserHasNoBookings() throws Exception {
		User savedUser = userRepository.save(User.builder()
		                                         .firstName("ToDelete")
		                                         .lastName("User")
		                                         .email("delete.me@example.com")
		                                         .passwordHash("password123")
		                                         .build());

		// Fix: pass the saved entity directly so @AuthenticationPrincipal resolves correctly
		mockMvc.perform(delete("/api/v1/users/me")
				                .with(user(savedUser)))
		       .andExpect(status().isNoContent());

		assertThat(userRepository.findByEmail("delete.me@example.com")).isEmpty();
	}
}