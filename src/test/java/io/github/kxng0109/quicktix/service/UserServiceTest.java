package io.github.kxng0109.quicktix.service;

import io.github.kxng0109.quicktix.dto.request.CreateUserRequest;
import io.github.kxng0109.quicktix.dto.response.UserResponse;
import io.github.kxng0109.quicktix.entity.Booking;
import io.github.kxng0109.quicktix.entity.User;
import io.github.kxng0109.quicktix.enums.Role;
import io.github.kxng0109.quicktix.exception.ResourceInUseException;
import io.github.kxng0109.quicktix.exception.UserExistsException;
import io.github.kxng0109.quicktix.repositories.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {
	private final Long userId = 100L;
	private final String userEmail = "test@user.test.com";

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@InjectMocks
	private UserService userService;

	private CreateUserRequest request;
	private User user;

	@BeforeEach
	public void setUp() {
		request = CreateUserRequest
				.builder()
				.firstName("test")
				.lastName("user")
				.email(userEmail)
				.phoneNumber("+23457849")
				.password("password123")
				.build();

		user = User.builder()
		           .id(userId)
		           .firstName("test")
		           .lastName("user")
		           .email(userEmail)
		           .phoneNumber("+23457849")
		           .role(Role.USER)
		           .build();
	}

	@Test
	public void getUserById_should_returnUserResponse_whenIdExists() {
		when(userRepository.findById(userId))
				.thenReturn(Optional.ofNullable(user));

		UserResponse result = userService.getUserById(userId);

		assertNotNull(result);
		assertEquals(userId, result.id());

		verify(userRepository).findById(userId);
	}

	@Test
	public void getUserById_should_throwEntityNotFoundException_whenIdDoesNotExists() {
		when(userRepository.findById(userId))
				.thenReturn(Optional.empty());

		assertThrows(
				EntityNotFoundException.class,
				() -> userService.getUserById(userId)
		);

		verify(userRepository).findById(userId);
	}

	@Test
	public void getUser_should_returnUserResponse_whenCurrentUserIsValid() {
		UserResponse result = userService.getUser(user);

		assertNotNull(result);
		assertEquals(userEmail, result.email());
	}

	@Test
	public void updateUser_should_returnUserResponseWIthUpdatedUserDetails_whenRequestIsValidAndCurrentUserExists() {
		when(userRepository.findById(userId))
				.thenReturn(Optional.of(user));
		when(passwordEncoder.encode(request.password()))
				.thenReturn("encodedPassword");
		when(userRepository.save(any(User.class)))
				.thenAnswer(i -> i.getArgument(0));

		UserResponse response = userService.updateUser(request, user);

		assertNotNull(response);
		assertEquals(request.email(), response.email());
		assertEquals(userId, response.id());

		verify(userRepository).findById(userId);
		verify(userRepository, never()).existsByEmail(anyString());
		verify(passwordEncoder).encode(request.password());
		verify(userRepository).save(any(User.class));
	}

	@Test
	public void updateUserById_should_returnUserResponseWIthUpdatedUserDetails_whenRequestIsValidAndUserByIdExists() {
		User currentUser = User.builder()
		                       .id(userId)
		                       .email(userEmail)
		                       .role(Role.USER)
		                       .build();

		when(userRepository.findById(userId))
				.thenReturn(Optional.of(user));
		when(passwordEncoder.encode(request.password()))
				.thenReturn("encodedPassword");
		when(userRepository.save(any(User.class)))
				.thenAnswer(i -> i.getArgument(0));

		UserResponse response = userService.updateUserById(userId, request, currentUser);

		assertNotNull(response);
		assertEquals(request.email(), response.email());
		assertEquals(userId, response.id());

		verify(userRepository).findById(userId);
		verify(userRepository, never()).existsByEmail(anyString());
		verify(passwordEncoder).encode(request.password());
		verify(userRepository).save(any(User.class));
	}

	@Test
	public void updateUserById_should_throwAccessDeniedException_whenUserTriesToUpdateAnotherUser() {
		User anotherCurrentUser = User.builder()
		                              .id(999L)
		                              .email("another@test.com")
		                              .role(Role.USER)
		                              .build();

		when(userRepository.findById(userId))
				.thenReturn(Optional.of(user));

		assertThrows(
				AccessDeniedException.class,
				() -> userService.updateUserById(userId, request, anotherCurrentUser)
		);

		verify(userRepository).findById(userId);
		verify(userRepository, never()).save(any(User.class));
	}

	@Test
	public void updateUserById_should_throwEntityNotFoundException_whenUserByIdDoesNotExist() {
		when(userRepository.findById(userId))
				.thenReturn(Optional.empty());

		assertThrows(
				EntityNotFoundException.class,
				() -> userService.updateUserById(userId, request, user)
		);

		verify(userRepository).findById(userId);
	}

	@Test
	public void updateUserById_should_throwUserExistsException_whenNewEmailIsTakenByAnotherUser() {
		String newTakenEmail = "taken@test.com";
		CreateUserRequest updateRequest = CreateUserRequest.builder()
		                                                   .firstName("New")
		                                                   .lastName("Name")
		                                                   .email(newTakenEmail)
		                                                   .password("password123")
		                                                   .build();

		User currentUser = User.builder()
		                       .id(userId)
		                       .role(Role.USER)
		                       .build();

		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(userRepository.existsByEmail(newTakenEmail)).thenReturn(true);

		assertThrows(
				UserExistsException.class,
				() -> userService.updateUserById(userId, updateRequest, currentUser)
		);

		verify(userRepository).findById(userId);
		verify(userRepository).existsByEmail(newTakenEmail);
		verify(userRepository, never()).save(any(User.class));
	}

	@Test
	public void updateUserById_should_NOT_throwException_whenEmailIsUnchanged() {
		CreateUserRequest sameEmailRequest = CreateUserRequest.builder()
		                                                      .firstName("UpdatedName")
		                                                      .lastName("UpdatedLastName")
		                                                      .email(userEmail)
		                                                      .password("password123")
		                                                      .build();

		User currentUser = User.builder()
		                       .id(userId)
		                       .email(userEmail)
		                       .role(Role.USER)
		                       .build();

		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(passwordEncoder.encode("password123")).thenReturn("encodedPassword");
		when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

		UserResponse response = userService.updateUserById(userId, sameEmailRequest, currentUser);

		assertNotNull(response);
		verify(userRepository, never()).existsByEmail(anyString());
		verify(passwordEncoder).encode("password123");
		verify(userRepository).save(any(User.class));
	}

	@Test
	public void deleteUser_should_returnNothing_whenUserExists() {
		User currentUser = User.builder()
		                       .id(userId)
		                       .email(userEmail)
				.bookings(List.of())
		                       .build();

		when(userRepository.findByEmail(userEmail))
				.thenReturn(Optional.of(currentUser));

		userService.deleteUser(currentUser);

		verify(userRepository).findByEmail(userEmail);
		verify(userRepository).save(any(User.class));
	}

	@Test
	public void deleteUser_should_throwEntityNotFoundException_whenUserDoesNotExist() {
		User currentUser = User.builder()
		                       .id(userId)
		                       .email(userEmail)
		                       .build();

		when(userRepository.findByEmail(userEmail))
				.thenReturn(Optional.empty());

		assertThrows(
				EntityNotFoundException.class,
				() -> userService.deleteUser(currentUser)
		);

		verify(userRepository).findByEmail(userEmail);
		verify(userRepository, never()).save(any(User.class));
	}

	@Test
	public void deleteUser_should_throwResourceInUseException_whenUserHasBookings() {
		User currentUser = User.builder()
		                       .id(userId)
		                       .email(userEmail)
		                       .bookings(List.of(new Booking()))
		                       .build();

		when(userRepository.findByEmail(userEmail))
				.thenReturn(Optional.of(currentUser));

		ResourceInUseException ex = assertThrows(
				ResourceInUseException.class,
				() -> userService.deleteUser(currentUser)
		);

		assertEquals("Cannot delete user with existing bookings. Deactivate the account instead.", ex.getMessage());

		verify(userRepository).findByEmail(userEmail);
		verify(userRepository, never()).save(any(User.class));
	}

	@Test
	public void forceDeleteUser_should_returnNothing_whenUserExists() {
		User currentUser = User.builder()
		                       .id(userId)
		                       .email(userEmail)
		                       .bookings(List.of())
		                       .build();

		when(userRepository.findById(userId))
				.thenReturn(Optional.of(currentUser));

		userService.forceDeleteUser(userId);

		verify(userRepository).findById(userId);
		verify(userRepository).save(any(User.class));
	}

	@Test
	public void forceDeleteUser_should_throwEntityNotFoundException_whenUserDoesNotExist() {
		when(userRepository.findById(userId))
				.thenReturn(Optional.empty());

		assertThrows(
				EntityNotFoundException.class,
				() -> userService.forceDeleteUser(userId)
		);

		verify(userRepository).findById(userId);
		verify(userRepository, never()).save(any(User.class));
	}

	@Test
	public void forceDeleteUser_should_stillDeactivateUser_whenUserHasBookings() {
		User currentUser = User.builder()
		                       .id(userId)
		                       .email(userEmail)
		                       .bookings(List.of(new Booking()))
		                       .build();

		when(userRepository.findById(userId))
				.thenReturn(Optional.of(currentUser));

		userService.forceDeleteUser(userId);

		verify(userRepository).findById(userId);
		verify(userRepository).save(any(User.class));
	}
}