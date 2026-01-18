package io.github.kxng0109.quicktix.service;

import io.github.kxng0109.quicktix.dto.request.CreateUserRequest;
import io.github.kxng0109.quicktix.dto.response.UserResponse;
import io.github.kxng0109.quicktix.entity.Booking;
import io.github.kxng0109.quicktix.entity.User;
import io.github.kxng0109.quicktix.exception.UserExistsException;
import io.github.kxng0109.quicktix.repositories.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
                .build();

        user = User.builder()
                   .id(userId)
                   .email(userEmail)
                   .build();
    }

    @Test
    public void createUser_should_returnAUserResponse_whenRequestIsValid() {
        when(userRepository.existsByEmail(userEmail))
                .thenReturn(false);
        when(userRepository.save(any(User.class)))
                .thenAnswer(i -> i.getArgument(0));

        UserResponse response = userService.createUser(request);

        assertNotNull(response);
        assertEquals(userEmail, response.email());

        verify(userRepository).existsByEmail(userEmail);
        verify(userRepository).save(any(User.class));
    }

    @Test
    public void createUser_should_throwUserExistsException_whenEmailAlreadyExists() {
        when(userRepository.existsByEmail(userEmail))
                .thenReturn(true);

        UserExistsException ex = assertThrows(
                UserExistsException.class,
                () -> userService.createUser(request)
        );

        assertEquals(new UserExistsException().getMessage(), ex.getMessage());

        verify(userRepository).existsByEmail(userEmail);
        verify(userRepository, never()).save(any(User.class));


    }

    @Test
    public void getUserById_should_returnUserId_whenIdExists() {
        when(userRepository.findById(userId))
                .thenReturn(
                        Optional.ofNullable(user)
                );

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
    public void getUserByEmail_should_returnUserEmail_whenEmailExists() {
        when(userRepository.findByEmail(userEmail))
                .thenReturn(
                        Optional.ofNullable(user)
                );

        UserResponse result = userService.getUserByEmail(userEmail);

        assertNotNull(result);
        assertEquals(userEmail, result.email());

        verify(userRepository).findByEmail(userEmail);
    }

    @Test
    public void getUserByEmail_should_throwEntityNotFoundException_whenEmailDoesNotExists() {
        when(userRepository.findByEmail(userEmail))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> userService.getUserByEmail(userEmail)
        );

        verify(userRepository).findByEmail(userEmail);
    }

    @Test
    public void updateUserById_should_returnUserResponseWIthUpdatedUserDetails_whenRequestIsValidAndUserByIdExists() {
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class)))
                .thenAnswer(i -> i.getArgument(0));

        UserResponse response = userService.updateUserById(userId, request);

        assertNotNull(response);
        assertEquals(userEmail, response.email());
        assertEquals(userId, response.id());

        verify(userRepository).findById(userId);
        verify(userRepository).save(any(User.class));
    }

    @Test
    public void updateUserById_should_throwEntityNotFoundException_whenUserByIdDoesNotExist() {
        when(userRepository.findById(userId))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> userService.updateUserById(userId, request)
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
                                                           .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail(newTakenEmail)).thenReturn(true);

        assertThrows(
                UserExistsException.class,
                () -> userService.updateUserById(userId, updateRequest)
        );

        verify(userRepository).findById(userId);
        verify(userRepository).existsByEmail(newTakenEmail);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    public void updateUserById_should_NOT_throwException_whenEmailIsUnchanged() {
        CreateUserRequest sameEmailRequest = CreateUserRequest.builder()
                                                              .firstName("UpdatedName")
                                                              .email(userEmail)
                                                              .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse response = userService.updateUserById(userId, sameEmailRequest);

        assertNotNull(response);
        verify(userRepository, never()).existsByEmail(anyString());
        verify(userRepository).save(any(User.class));
    }

    @Test
    public void deleteUserByEmail_should_returnNothing_whenUserExists() {
        when(userRepository.findByEmail(userEmail))
                .thenReturn(Optional.ofNullable(user));

        userService.deleteUserByEmail(userEmail);

        verify(userRepository).findByEmail(userEmail);
        verify(userRepository).delete(user);
    }

    @Test
    public void deleteUserByEmail_should_throwEntityNotFoundException_whenUserDoesNotExist() {
        when(userRepository.findByEmail(userEmail))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> userService.deleteUserByEmail(userEmail)
        );

        verify(userRepository).findByEmail(userEmail);
        verify(userRepository, never()).delete(user);
    }

    @Test
    public void deleteUserByEmail_should_throwIllegalStateException_whenUserHasBookings() {
        User userWithBookings = User.builder()
                                    .id(userId)
                                    .email(userEmail)
                                    .bookings(List.of(new Booking()))
                                    .build();

        when(userRepository.findByEmail(userEmail))
                .thenReturn(Optional.of(userWithBookings));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> userService.deleteUserByEmail(userEmail)
        );

        assertEquals("Cannot delete user with existing bookings. Deactivate the account instead.", ex.getMessage());

        verify(userRepository).findByEmail(userEmail);
        verify(userRepository, never()).delete(any(User.class));
    }
}
