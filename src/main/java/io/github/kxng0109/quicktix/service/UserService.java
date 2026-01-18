package io.github.kxng0109.quicktix.service;

import io.github.kxng0109.quicktix.dto.request.CreateUserRequest;
import io.github.kxng0109.quicktix.dto.response.UserResponse;
import io.github.kxng0109.quicktix.entity.User;
import io.github.kxng0109.quicktix.exception.UserExistsException;
import io.github.kxng0109.quicktix.repositories.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new UserExistsException();
        }

        String phoneNumber = request.phoneNumber() != null ? request.phoneNumber() : null;

        User user = User.builder()
                        .firstName(request.firstName())
                        .lastName(request.lastName())
                        .email(request.email())
                        .phoneNumber(phoneNumber)
                        .build();

        User savedUser = userRepository.save(user);
        return buildUserResponse(savedUser);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(long id) {
        User user = userRepository.findById(id)
                                  .orElseThrow(
                                          () -> new EntityNotFoundException("User not found with id: " + id)
                                  );

        return buildUserResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                                  .orElseThrow(
                                          () -> new EntityNotFoundException("User not found with email: " + email)
                                  );

        return buildUserResponse(user);
    }

    @Transactional
    public UserResponse updateUserById(Long userId, CreateUserRequest request) {
        User user = getUserEntityById(userId);

        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        if(!user.getEmail().equalsIgnoreCase(request.email())) {
            if(userRepository.existsByEmail(request.email())) {
                throw new UserExistsException();
            }

            user.setEmail(request.email());
        }
        String phoneNumber = request.phoneNumber() != null ? request.phoneNumber() : null;
        user.setPhoneNumber(phoneNumber);

        User savedUser = userRepository.save(user);

        return buildUserResponse(savedUser);
    }

    @Transactional
    public void deleteUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                                  .orElseThrow(
                                          () -> new EntityNotFoundException("User not found with email: " + email)
                                  );

        // Don't delete users with bookings
        boolean hasBookings = user.getBookings() != null && !user.getBookings().isEmpty();
        if (hasBookings) {
            throw new IllegalStateException("Cannot delete user with existing bookings. Deactivate the account instead.");
        }

        userRepository.delete(user);
    }


    @Transactional(readOnly = true)
    User getUserEntityById(long userId) {
        return userRepository.findById(userId)
                             .orElseThrow(
                                     () -> new EntityNotFoundException("User not found with id: " + userId)
                             );
    }

    private UserResponse buildUserResponse(User user) {
        return UserResponse.builder()
                           .id(user.getId())
                           .firstName(user.getFirstName())
                           .lastName(user.getLastName())
                           .email(user.getEmail())
                           .phoneNumber(user.getPhoneNumber())
                           .build();
    }
}
