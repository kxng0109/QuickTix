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

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        boolean isANewUser = userRepository.existsByEmail(request.email());
        if (isANewUser) {
            throw new UserExistsException();
        }

        return constructAndSaveUser(request, isANewUser);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(long id) {
        User user = userRepository.findById(id)
                                  .orElseThrow(
                                          () -> new EntityNotFoundException("User not found with id: " + id)
                                  );

        return UserResponse
                .builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getFirstName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .build();
    }

    @Transactional(readOnly = true)
    public UserResponse getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                                  .orElseThrow(
                                          () -> new EntityNotFoundException("User not found with email: " + email)
                                  );

        return UserResponse
                .builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getFirstName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .build();
    }

    @Transactional
    public UserResponse updateUser(CreateUserRequest request) {
        boolean isANewUser = userRepository.existsByEmail(request.email());

        if (!isANewUser) {
            throw new EntityNotFoundException("User not found with email: " + request.email());
        }

        return constructAndSaveUser(request, isANewUser);
    }

    @Transactional
    public void deleteUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                                  .orElseThrow(
                                          () -> new EntityNotFoundException("User not found with email: " + email)
                                  );

        userRepository.delete(user);
    }


    private UserResponse constructAndSaveUser(CreateUserRequest request, boolean isANewUser) {
        String phoneNumber = request.phoneNumber() != null ? request.phoneNumber() : null;

        User user = User.builder()
                        .firstName(request.firstName())
                        .lastName(request.lastName())
                        .email(request.email())
                        .phoneNumber(phoneNumber)
                        .updatedAt(LocalDateTime.now())
                        .build();

        //If we are just creating a user, then get the createdAt to be the updatedAt
        //We are doing this because we don't need to set the createdAt for existing accounts
        if (!isANewUser) {
            user.setCreatedAt(user.getUpdatedAt());
        }

        User savedUser = userRepository.save(user);

        return UserResponse.builder()
                           .id(savedUser.getId())
                           .firstName(savedUser.getFirstName())
                           .lastName(savedUser.getLastName())
                           .email(savedUser.getEmail())
                           .phoneNumber(savedUser.getPhoneNumber())
                           .build();
    }
}
