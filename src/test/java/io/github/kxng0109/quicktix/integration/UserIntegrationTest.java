package io.github.kxng0109.quicktix.integration;

import io.github.kxng0109.quicktix.dto.request.CreateUserRequest;
import io.github.kxng0109.quicktix.dto.request.LoginRequest;
import io.github.kxng0109.quicktix.entity.User;
import io.github.kxng0109.quicktix.enums.Role;
import io.github.kxng0109.quicktix.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class UserIntegrationTest extends BaseIntegrationTest {

	@Autowired
	private UserRepository userRepository;
	@Autowired
	private PasswordEncoder passwordEncoder;

	private User savedUser;

	@BeforeEach
	void setUp() {
		User user = User.builder()
		                .firstName("Jane")
		                .lastName("Smith")
		                .email("jane.smith@example.com")
		                .passwordHash(passwordEncoder.encode("password123"))
		                .build();
		savedUser = userRepository.save(user);
	}

	@Test
	void getUserById_shouldReturnUser_whenUserExists() throws Exception {
		String adminToken = getAdminToken();

		mockMvc.perform(get("/api/v1/users/{id}", savedUser.getId())
				                .header("Authorization", "Bearer " + adminToken))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("$.id").value(savedUser.getId()))
		       .andExpect(jsonPath("$.firstName").value("Jane"))
		       .andExpect(jsonPath("$.email").value("jane.smith@example.com"));
	}

	@Test
	void getUserById_shouldReturn404_whenUserDoesNotExist() throws Exception {
		String adminToken = getAdminToken();

		mockMvc.perform(get("/api/v1/users/{id}", 99999L)
				                .header("Authorization", "Bearer " + adminToken))
		       .andExpect(status().isNotFound());
	}

	@Test
	void updateUserById_shouldModifyUserInDatabase_whenRequestIsValid() throws Exception {
		CreateUserRequest updateRequest = CreateUserRequest.builder()
		                                                   .firstName("Updated")
		                                                   .lastName("Name")
		                                                   .email("updated@example.com")
		                                                   .password("password123")
		                                                   .phoneNumber("+2345555555555")
		                                                   .build();

		mockMvc.perform(put("/api/v1/users/{id}", savedUser.getId())
				                .contentType(MediaType.APPLICATION_JSON)
				                .header("Authorization", "Bearer " + getAdminToken())
				                .content(objectMapper.writeValueAsString(updateRequest)))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("$.firstName").value("Updated"))
		       .andExpect(jsonPath("$.email").value("updated@example.com"));

		User updatedUser = userRepository.findById(savedUser.getId()).orElseThrow();
		assertThat(updatedUser.getFirstName()).isEqualTo("Updated");
		assertThat(updatedUser.getEmail()).isEqualTo("updated@example.com");
	}

	@Test
	void deleteUserMe_shouldRemoveUserFromDatabase_whenUserHasNoBookings() throws Exception {
		mockMvc.perform(delete("/api/v1/users/me")
				                .header("Authorization", "Bearer " + getUserToken())
		       )
		       .andExpect(status().isNoContent());

		assertThat(userRepository.findByEmail("jane.smith@example.com")).isEmpty();
	}

	private String getUserToken() throws Exception{
		LoginRequest adminLoginRequest = LoginRequest.builder()
		                                             .email("jane.smith@example.com")
		                                             .password("password123")
		                                             .build();

		MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/login")
				                                           .contentType(MediaType.APPLICATION_JSON)
				                                           .content(objectMapper.writeValueAsString(adminLoginRequest)))
		                                  .andExpect(status().isOk())
		                                  .andReturn();

		return objectMapper.readTree(registerResult.getResponse().getContentAsString()).get("token").asText();
	}

	private String getAdminToken() throws Exception {
		User admin = userRepository.save(User.builder()
		                                     .firstName("Admin")
		                                     .lastName("User")
		                                     .email("admin@test.com")
		                                     .passwordHash(passwordEncoder.encode("password123"))
		                                     .role(Role.ADMIN)
		                                     .build());

		LoginRequest adminLoginRequest = LoginRequest.builder()
		                                .email("admin@test.com")
		                                .password("password123")
		                                .build();

		MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/login")
				                                           .contentType(MediaType.APPLICATION_JSON)
				                                           .content(objectMapper.writeValueAsString(adminLoginRequest)))
		                                  .andExpect(status().isOk())
		                                  .andReturn();

		return objectMapper.readTree(registerResult.getResponse().getContentAsString()).get("token").asText();
	}
}