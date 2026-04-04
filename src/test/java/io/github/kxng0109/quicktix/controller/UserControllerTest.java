package io.github.kxng0109.quicktix.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kxng0109.quicktix.dto.request.CreateUserRequest;
import io.github.kxng0109.quicktix.dto.response.BookingResponse;
import io.github.kxng0109.quicktix.dto.response.UserResponse;
import io.github.kxng0109.quicktix.entity.User;
import io.github.kxng0109.quicktix.enums.Role;
import io.github.kxng0109.quicktix.exception.ResourceInUseException;
import io.github.kxng0109.quicktix.exception.UserExistsException;
import io.github.kxng0109.quicktix.service.BookingService;
import io.github.kxng0109.quicktix.service.CustomUserDetailsService;
import io.github.kxng0109.quicktix.service.JwtService;
import io.github.kxng0109.quicktix.service.UserService;
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

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
public class UserControllerTest {

	private final Long userId = 100L;
	private final String email = "matpet1@gmail.com";

	@Autowired
	private MockMvc mockMvc;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@MockitoBean
	private UserService userService;

	@MockitoBean
	private BookingService bookingService;

	@MockitoBean
	private JwtService jwtService;

	@MockitoBean
	private CustomUserDetailsService userDetailsService;

	private CreateUserRequest request;
	private CreateUserRequest badRequest;
	private UserResponse response;
	private User currentUser;

	@BeforeEach
	public void setup() {
		request = CreateUserRequest.builder()
		                           .firstName("Matthew")
		                           .lastName("Peter")
		                           .email(email)
		                           .password("Password@123")
		                           .phoneNumber("+23456546534")
		                           .build();

		badRequest = CreateUserRequest.builder().build();

		response = UserResponse.builder()
		                       .id(userId)
		                       .firstName(request.firstName())
		                       .lastName(request.lastName())
		                       .email(request.email())
		                       .phoneNumber(request.phoneNumber())
		                       .build();

		currentUser = User.builder()
		                  .id(userId)
		                  .email(email)
		                  .role(Role.ADMIN)
		                  .passwordHash("hashed")
		                  .build();
	}

	@Test
	public void getUserById_should_return200OkAndUserResponse_whenUserExists() throws Exception {
		when(userService.getUserById(anyLong()))
				.thenReturn(response);

		mockMvc.perform(
				       get("/api/v1/users/{id}", userId)
						       .with(user(currentUser))
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isOk())
		       .andExpect(jsonPath("$.firstName").value(request.firstName()))
		       .andExpect(jsonPath("$.lastName").value(request.lastName()))
		       .andExpect(jsonPath("$.id").value(userId));
	}

	@Test
	public void getUserById_should_return400BadRequest_whenIdIsInvalid() throws Exception {
		String uriTemplate = "/api/v1/users/-1";

		mockMvc.perform(
				       get(uriTemplate)
						       .with(user(currentUser))
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
				       get("/api/v1/users/{id}", userId)
						       .with(user(currentUser))
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isNotFound())
		       .andExpect(jsonPath("$.statusCode").value(404))
		       .andExpect(jsonPath("$.path").value("/api/v1/users/" + userId));
	}

	@Test
	public void getBookingsByUser_should_return200OkAndAPageOfBookingResponse_whenIdIsValid() throws Exception {
		Page<BookingResponse> bookingResponsePage = new PageImpl<>(
				List.of(BookingResponse.builder().id(100L).eventStartDateTime(Instant.now()).build())
		);

		when(bookingService.getBookingsByUser(anyLong(), any(Pageable.class), any(User.class)))
				.thenReturn(bookingResponsePage);

		mockMvc.perform(
				       get("/api/v1/users/{userId}/bookings", userId)
						       .with(user(currentUser))
						       .param("page", "0")
						       .param("size", "10")
						       .param("sort", "id,desc")
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isOk())
		       .andExpect(jsonPath("$.content.length()").value(1))
		       .andExpect(jsonPath("$.content[0].id").value(100L))
		       .andExpect(jsonPath("$.page.totalElements").value(1))
		       .andExpect(jsonPath("$.page.size").value(1));
	}

	@Test
	public void getBookingsByUser_should_useDefaults_whenPageableParamIsNotPassed() throws Exception {
		Page<BookingResponse> bookingResponsePage = new PageImpl<>(
				List.of(BookingResponse.builder().id(100L).eventStartDateTime(Instant.now()).build())
		);

		when(bookingService.getBookingsByUser(anyLong(), any(Pageable.class), any(User.class)))
				.thenReturn(bookingResponsePage);

		mockMvc.perform(
				       get("/api/v1/users/{userId}/bookings", userId)
						       .with(user(currentUser))
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isOk())
		       .andExpect(jsonPath("$.content.length()").value(1))
		       .andExpect(jsonPath("$.content[0].id").value(100L))
		       .andExpect(jsonPath("$.page.totalElements").value(1))
		       .andExpect(jsonPath("$.page.size").value(1));
	}

	@Test
	public void getBookingsByUser_should_return400BadRequest_whenIdIsInvalid() throws Exception {
		mockMvc.perform(
				       get("/api/v1/users/{userId}/bookings", -1)
						       .with(user(currentUser))
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isBadRequest())
		       .andExpect(jsonPath("$.statusCode").value(400))
		       .andExpect(jsonPath("$.path").value("/api/v1/users/-1/bookings"));

		verify(bookingService, never()).getBookingsByUser(anyLong(), any(Pageable.class), any(User.class));
	}

	@Test
	public void getBookingsByUser_should_return404NotFound_whenUserIsNotFound() throws Exception {
		doThrow(EntityNotFoundException.class).when(bookingService)
		                                      .getBookingsByUser(anyLong(), any(Pageable.class), any(User.class));

		mockMvc.perform(
				       get("/api/v1/users/{userId}/bookings", userId)
						       .with(user(currentUser))
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isNotFound())
		       .andExpect(jsonPath("$.statusCode").value(404))
		       .andExpect(jsonPath("$.path").value("/api/v1/users/100/bookings"));
	}

	@Test
	public void getUser_should_return200OkAndUserResponse_whenUserIsAuthenticated() throws Exception {
		when(userService.getUser(any(User.class)))
				.thenReturn(response);

		mockMvc.perform(
				       get("/api/v1/users/me")
						       .with(user(currentUser))
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isOk())
		       .andExpect(jsonPath("$.id").value(userId))
		       .andExpect(jsonPath("$.email").value(email));
	}

	@Test
	public void updateUser_should_return200OkAndUserResponse_whenRequestIsValid() throws Exception {
		when(userService.updateUser(any(CreateUserRequest.class), any(User.class)))
				.thenReturn(response);

		mockMvc.perform(
				       put("/api/v1/users/me")
						       .with(user(currentUser))
						       .contentType(MediaType.APPLICATION_JSON)
						       .content(objectMapper.writeValueAsString(request))
		       ).andExpect(status().isOk())
		       .andExpect(jsonPath("$.firstName").value(request.firstName()))
		       .andExpect(jsonPath("$.lastName").value(request.lastName()))
		       .andExpect(jsonPath("$.id").value(userId));
	}

	@Test
	public void updateUser_should_return400BadRequest_whenRequestIsInvalid() throws Exception {
		mockMvc.perform(
				       put("/api/v1/users/me")
						       .with(user(currentUser))
						       .contentType(MediaType.APPLICATION_JSON)
						       .content(objectMapper.writeValueAsString(badRequest))
		       ).andExpect(status().isBadRequest())
		       .andExpect(jsonPath("$.firstName").value("First name can't be blank"))
		       .andExpect(jsonPath("$.email").value("Email can't be blank"));

		verify(userService, never()).updateUser(any(CreateUserRequest.class), any(User.class));
	}

	@Test
	public void updateUser_should_return409Conflict_whenEmailAlreadyExists() throws Exception {
		when(userService.updateUser(any(CreateUserRequest.class), any(User.class)))
				.thenThrow(UserExistsException.class);

		mockMvc.perform(
				       put("/api/v1/users/me")
						       .with(user(currentUser))
						       .contentType(MediaType.APPLICATION_JSON)
						       .content(objectMapper.writeValueAsString(request))
		       ).andExpect(status().isConflict())
		       .andExpect(jsonPath("$.statusCode").value(409))
		       .andExpect(jsonPath("$.path").value("/api/v1/users/me"));
	}

	@Test
	public void updateUserById_should_return200OkAndUserResponse_whenUserExists() throws Exception {
		when(userService.updateUserById(anyLong(), any(CreateUserRequest.class), any(User.class)))
				.thenReturn(response);

		mockMvc.perform(
				       put("/api/v1/users/{id}", userId)
						       .with(user(currentUser))
						       .contentType(MediaType.APPLICATION_JSON)
						       .content(objectMapper.writeValueAsString(request))
		       ).andExpect(status().isOk())
		       .andExpect(jsonPath("$.firstName").value(request.firstName()))
		       .andExpect(jsonPath("$.lastName").value(request.lastName()))
		       .andExpect(jsonPath("$.id").value(userId));
	}

	@Test
	public void updateUserById_should_return400BadRequest_whenIdIsInvalid() throws Exception {
		mockMvc.perform(
				       put("/api/v1/users/{id}", -1)
						       .with(user(currentUser))
						       .contentType(MediaType.APPLICATION_JSON)
						       .content(objectMapper.writeValueAsString(request))
		       ).andExpect(status().isBadRequest())
		       .andExpect(jsonPath("$.statusCode").value(400))
		       .andExpect(jsonPath("$.path").value("/api/v1/users/-1"));

		verify(userService, never()).updateUserById(anyLong(), any(CreateUserRequest.class), any(User.class));
	}

	@Test
	public void updateUserById_should_return400BadRequest_whenBodyIsInvalid() throws Exception {
		mockMvc.perform(
				       put("/api/v1/users/{id}", userId)
						       .with(user(currentUser))
						       .contentType(MediaType.APPLICATION_JSON)
						       .content(objectMapper.writeValueAsString(badRequest))
		       ).andExpect(status().isBadRequest())
		       .andExpect(jsonPath("$.email").value("Email can't be blank"));

		verify(userService, never()).updateUserById(anyLong(), any(CreateUserRequest.class), any(User.class));
	}

	@Test
	public void updateUserById_should_return404NotFound_whenUserDoesNotExist() throws Exception {
		String uriTemplate = "/api/v1/users/" + userId;

		when(userService.updateUserById(anyLong(), any(CreateUserRequest.class), any(User.class)))
				.thenThrow(EntityNotFoundException.class);

		mockMvc.perform(
				       put(uriTemplate)
						       .with(user(currentUser))
						       .contentType(MediaType.APPLICATION_JSON)
						       .content(objectMapper.writeValueAsString(request))
		       ).andExpect(status().isNotFound())
		       .andExpect(jsonPath("$.statusCode").value(404))
		       .andExpect(jsonPath("$.path").value(uriTemplate));
	}

	@Test
	public void updateUserById_should_return409Conflict_whenNewEmailAlreadyExists() throws Exception {
		String uriTemplate = "/api/v1/users/" + userId;

		when(userService.updateUserById(anyLong(), any(CreateUserRequest.class), any(User.class)))
				.thenThrow(UserExistsException.class);

		mockMvc.perform(
				       put(uriTemplate)
						       .with(user(currentUser))
						       .contentType(MediaType.APPLICATION_JSON)
						       .content(objectMapper.writeValueAsString(request))
		       ).andExpect(status().isConflict())
		       .andExpect(jsonPath("$.statusCode").value(409))
		       .andExpect(jsonPath("$.path").value(uriTemplate));
	}

	@Test
	public void deleteUser_should_return204NoContent_whenUserExistsAndHasNoActiveBookings() throws Exception {
		doNothing().when(userService).deleteUser(any(User.class));

		mockMvc.perform(
				delete("/api/v1/users/me")
						.with(user(currentUser))
						.contentType(MediaType.APPLICATION_JSON)
		).andExpect(status().isNoContent());

		verify(userService).deleteUser(any(User.class));
	}

	@Test
	public void deleteUser_should_return404NotFound_whenUserDoesNotExist() throws Exception {
		doThrow(EntityNotFoundException.class)
				.when(userService).deleteUser(any(User.class));

		mockMvc.perform(
				       delete("/api/v1/users/me")
						       .with(user(currentUser))
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isNotFound())
		       .andExpect(jsonPath("$.statusCode").value(404))
		       .andExpect(jsonPath("$.path").value("/api/v1/users/me"));
	}

	@Test
	public void deleteUser_should_return409Conflict_whenUserHasActiveBookings() throws Exception {
		doThrow(ResourceInUseException.class)
				.when(userService).deleteUser(any(User.class));

		mockMvc.perform(
				       delete("/api/v1/users/me")
						       .with(user(currentUser))
						       .contentType(MediaType.APPLICATION_JSON)
		       ).andExpect(status().isConflict())
		       .andExpect(jsonPath("$.statusCode").value(409))
		       .andExpect(jsonPath("$.path").value("/api/v1/users/me"));
	}
}