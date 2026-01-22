package io.github.kxng0109.quicktix.controller;

import io.github.kxng0109.quicktix.dto.request.CreateUserRequest;
import io.github.kxng0109.quicktix.dto.response.UserResponse;
import io.github.kxng0109.quicktix.exception.ResourceInUseException;
import io.github.kxng0109.quicktix.exception.UserExistsException;
import io.github.kxng0109.quicktix.service.UserService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
public class UserControllerTest {

	private final Long userId = 100L;
	private final String email = "matpet1@gmail.com";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private UserService userService;

	private CreateUserRequest request;
	private CreateUserRequest badRequest;
	private UserResponse response;

	@BeforeEach
	public void setup() {
		request = CreateUserRequest.builder()
		                           .firstName("Matthew")
		                           .lastName("Peter")
		                           .email(email)
		                           .phoneNumber("+23456546534")
		                           .build();

		badRequest = CreateUserRequest.builder().build();

		response = UserResponse.builder()
		                       .id(userId).firstName(request.firstName())
		                       .lastName(request.lastName())
		                       .email(request.email())
		                       .phoneNumber(request.phoneNumber())
		                       .build();
	}

	@Test
	public void createUser_should_return200OkAndUserResponse_whenRequestIsValid() throws Exception {
		when(userService.createUser(any(CreateUserRequest.class)))
				.thenReturn(response);

		mockMvc.perform(
				       post("/api/v1/users")
						       .contentType(MediaType.APPLICATION_JSON)
						       .content(objectMapper.writeValueAsString(request))
		       )
		       .andExpect(status().isCreated())
		       .andExpect(jsonPath("$.firstName").value(request.firstName()))
		       .andExpect(jsonPath("$.lastName").value(request.lastName()))
		       .andExpect(jsonPath("$.id").value(userId));
	}

	@Test
	public void createUser_should_return400BadRequest_whenRequestIsInvalid() throws Exception {
		mockMvc.perform(
				       post("/api/v1/users")
						       .contentType(MediaType.APPLICATION_JSON)
						       .content(objectMapper.writeValueAsString(badRequest))
		       ).andExpect(status().isBadRequest())
		       .andExpect(jsonPath("$.firstName").value("First name can't be blank"))
		       .andExpect(jsonPath("$.email").value("Email can't be blank"));

		verify(userService, never()).createUser(any(CreateUserRequest.class));
	}

	@Test
	public void createUser_should_return409Conflict_whenUserWithEmailAlreadyExists() throws Exception {
		when(userService.createUser(any(CreateUserRequest.class)))
				.thenThrow(UserExistsException.class);

		mockMvc.perform(
				post("/api/v1/users")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request))
		).andExpect(status().isConflict());
	}

	@Test
	public void getUserById_should_return200OkAndUserResponse_whenUserExists() throws Exception {
		when(userService.getUserById(anyLong()))
				.thenReturn(response);

		mockMvc.perform(
				       get("/api/v1/users/" + userId)
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isOk())
		       .andExpect(jsonPath("$.firstName").value(request.firstName()))
		       .andExpect(jsonPath("$.lastName").value(request.lastName()))
		       .andExpect(jsonPath("$.id").value(userId));
	}

	@Test
	public void getUserById_should_return400BadRequest_whenRequestIsInvalid() throws Exception {
		String uriTemplate = "/api/v1/users/" + -1;

		mockMvc.perform(
				       get(uriTemplate)
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isBadRequest())
		       .andExpect(jsonPath("$.statusCode").value(400))
		       .andExpect(jsonPath("$.path").value(uriTemplate));

		verify(userService, never()).getUserById(anyLong());
	}

	@Test
	public void getUserById_should_return404NotFound_whenUserDoesNotExist() throws Exception {
		when(userService.getUserById(anyLong()))
				.thenThrow(EntityNotFoundException.class);

		mockMvc.perform(
				get("/api/v1/users/" + userId)
						.contentType(MediaType.APPLICATION_JSON)
		).andExpect(status().isNotFound());
	}

	@Test
	public void getUserByEmail_should_return200OkAndUserResponse_whenUserExists() throws Exception {
		when(userService.getUserByEmail(anyString()))
				.thenReturn(response);

		mockMvc.perform(
				       get("/api/v1/users/email/" + email)
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isOk())
		       .andExpect(jsonPath("$.firstName").value(request.firstName()))
		       .andExpect(jsonPath("$.lastName").value(request.lastName()))
		       .andExpect(jsonPath("$.id").value(userId));
	}

	@Test
	public void getUserByEmail_should_return400BadRequest_whenRequestIsInvalid() throws Exception {
		String uriTemplate = "/api/v1/users/email/" + "notemail.abc";

		mockMvc.perform(
				       get(uriTemplate)
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isBadRequest())
		       .andExpect(jsonPath("$.statusCode").value(400))
		       .andExpect(jsonPath("$.path").value(uriTemplate));

		verify(userService, never()).getUserByEmail(anyString());
	}

	@Test
	public void getUserByEmail_should_return404NotFound_whenUserIsNotFound() throws Exception {
		String uriTemplate = "/api/v1/users/email/" + email;

		when(userService.getUserByEmail(anyString()))
				.thenThrow(EntityNotFoundException.class);

		mockMvc.perform(
				       get(uriTemplate)
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isNotFound())
		       .andExpect(jsonPath("$.statusCode").value(404))
		       .andExpect(jsonPath("$.path").value(uriTemplate));
	}

	@Test
	public void updateUserById_should_return200OkAndUserResponse_whenUserExists() throws Exception {
		when(userService.updateUserById(anyLong(), any(CreateUserRequest.class)))
				.thenReturn(response);

		mockMvc.perform(
				       put("/api/v1/users/" + userId)
						       .contentType(MediaType.APPLICATION_JSON)
						       .content(objectMapper.writeValueAsString(request))
		       ).andExpect(status().isOk())
		       .andExpect(jsonPath("$.firstName").value(request.firstName()))
		       .andExpect(jsonPath("$.lastName").value(request.lastName()))
		       .andExpect(jsonPath("$.id").value(userId));
	}

	@Test
	public void updateUserById_should_return400BadRequest_whenIdIsInvalid() throws Exception {
		mockMvc.perform(put("/api/v1/users/" + -1)
				                .contentType(MediaType.APPLICATION_JSON)
				                .content(objectMapper.writeValueAsString(request)))
		       .andExpect(status().isBadRequest())
		       .andExpect(jsonPath("$.message").exists());
	}

	@Test
	public void updateUserById_should_return400BadRequest_whenBodyIsInvalid() throws Exception {
		mockMvc.perform(put("/api/v1/users/" + userId)
				                .contentType(MediaType.APPLICATION_JSON)
				                .content(objectMapper.writeValueAsString(badRequest)))
		       .andExpect(status().isBadRequest())
		       .andExpect(jsonPath("$.email").value("Email can't be blank"));
	}

	@Test
	public void updateUserById_should_return404NotFound_whenUserDoesNotExist() throws Exception {
		String uriTemplate = "/api/v1/users/" + userId;

		when(userService.updateUserById(anyLong(), any(CreateUserRequest.class)))
				.thenThrow(EntityNotFoundException.class);

		mockMvc.perform(
				       put(uriTemplate)
						       .contentType(MediaType.APPLICATION_JSON)
						       .content(objectMapper.writeValueAsString(request))
		       ).andExpect(status().isNotFound())
		       .andExpect(jsonPath("$.statusCode").value(404))
		       .andExpect(jsonPath("$.path").value(uriTemplate));
	}

	@Test
	public void updateUserById_should_return409Conflict_whenUserWithNewEmailExists() throws Exception {
		String uriTemplate = "/api/v1/users/" + userId;

		when(userService.updateUserById(anyLong(), any(CreateUserRequest.class)))
				.thenThrow(UserExistsException.class);

		mockMvc.perform(
				       put(uriTemplate)
						       .contentType(MediaType.APPLICATION_JSON)
						       .content(objectMapper.writeValueAsString(request))
		       ).andExpect(status().isConflict())
		       .andExpect(jsonPath("$.statusCode").value(409))
		       .andExpect(jsonPath("$.path").value(uriTemplate));
	}

	@Test
	public void deleteUserByEmail_should_return204NoContent_whenUserExistsAndHasNoActiveBookings() throws Exception {
		doNothing().when(userService).deleteUserByEmail(anyString());

		mockMvc.perform(
				delete("/api/v1/users/email/" + email)
						.contentType(MediaType.APPLICATION_JSON)
		).andExpect(status().isNoContent());

		verify(userService).deleteUserByEmail(anyString());
	}

	@Test
	public void deleteUserByEmail_should_return400BadRequest_whenRequestIsInvalid() throws Exception {
		String uriTemplate = "/api/v1/users/email/" + "notemail.abc";

		mockMvc.perform(
				       delete(uriTemplate)
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isBadRequest())
		       .andExpect(jsonPath("$.statusCode").value(400));

		verify(userService, never()).deleteUserByEmail(anyString());
	}

	@Test
	public void deleteUserByEmail_should_return404NotFound_whenUserDoesNotExist() throws Exception {
		String uriTemplate = "/api/v1/users/email/" + email;

		doThrow(EntityNotFoundException.class)
				.when(userService).deleteUserByEmail(anyString());

		mockMvc.perform(
				       delete(uriTemplate)
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isNotFound())
		       .andExpect(jsonPath("$.statusCode").value(404))
		       .andExpect(jsonPath("$.path").value(uriTemplate));
	}

	@Test
	public void deleteUserByEmail_should_return409Conflict_whenUserHasActiveBookings() throws Exception {
		String uriTemplate = "/api/v1/users/email/" + email;

		doThrow(ResourceInUseException.class)
				.when(userService).deleteUserByEmail(anyString());

		mockMvc.perform(
				       delete(uriTemplate)
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isConflict())
		       .andExpect(jsonPath("$.statusCode").value(409))
		       .andExpect(jsonPath("$.path").value(uriTemplate));
	}
}
