package io.github.kxng0109.quicktix.service;

import io.github.kxng0109.quicktix.dto.request.CreateUserRequest;
import io.github.kxng0109.quicktix.dto.response.UserResponse;
import io.github.kxng0109.quicktix.entity.User;
import io.github.kxng0109.quicktix.exception.ResourceInUseException;
import io.github.kxng0109.quicktix.exception.UserExistsException;
import io.github.kxng0109.quicktix.repositories.UserRepository;
import io.github.kxng0109.quicktix.utils.AssertOwnershipOrAdmin;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	@Transactional(readOnly = true)
	public UserResponse getUserById(long id) {
		User user = userRepository.findById(id)
		                          .orElseThrow(
				                          () -> new EntityNotFoundException("User not found with id: " + id)
		                          );

		return buildUserResponse(user);
	}

	@Transactional(readOnly = true)
	public UserResponse getUser(User currentUser) {
		return buildUserResponse(currentUser);
	}

	@Transactional
	public UserResponse updateUser(CreateUserRequest request, User currentUser){
		return updateUserById(currentUser.getId(), request, currentUser);
	}

	@Transactional
	public UserResponse updateUserById(Long userId, CreateUserRequest request, User currentUser) {
		User user = getUserEntityById(userId);

		AssertOwnershipOrAdmin.check(currentUser, user);

		user.setFirstName(request.firstName());
		user.setLastName(request.lastName());
		if (!user.getEmail().equalsIgnoreCase(request.email())) {
			if (userRepository.existsByEmail(request.email())) {
				throw new UserExistsException();
			}

			user.setEmail(request.email());
		}

		if (request.password() != null && !request.password().isBlank()) {
			user.setPasswordHash(passwordEncoder.encode(request.password()));
		}

		String phoneNumber = request.phoneNumber() != null ? request.phoneNumber() : null;
		user.setPhoneNumber(phoneNumber);

		User savedUser = userRepository.save(user);

		return buildUserResponse(savedUser);
	}

	//Actually just deactivates the account
	@Transactional
	public void deleteUser(User currentUser) {
		User user = userRepository.findByEmail(currentUser.getEmail())
		                          .orElseThrow(
				                          () -> new EntityNotFoundException("User not found with email: " + currentUser.getEmail())
		                          );

		// Don't delete users with bookings
		boolean hasBookings = user.getBookings() != null && !user.getBookings().isEmpty();
		if (hasBookings) {
			throw new ResourceInUseException(
					"Cannot delete user with existing bookings. Deactivate the account instead.");
		}

		deactivateUser(user);
	}

	/**
	 * Forcibly deactivates a user account while preserving database referential integrity.
	 * <p>
	 * In enterprise systems, hard-deleting a user who possesses financial records (Bookings, Payments)
	 * will trigger severe foreign key constraint violations. Instead, this method performs a GDPR-compliant "Soft Delete":
	 * <ul>
	 * <li>Scrambles all Personally Identifiable Information (PII) including email, name, and phone.</li>
	 * <li>Replaces the password hash with an un-hashable UUID to mathematically prevent future logins.</li>
	 * <li>Sets the {@code isActive} flag to {@code false}, which Spring Security uses to block authentication.</li>
	 * </ul>
	 * </p>
	 *
	 * @param userId The unique identifier of the user to be deactivated.
	 * @throws EntityNotFoundException if the user ID does not exist.
	 */
	@Transactional
	public void forceDeleteUser(Long userId){
		User user = userRepository.findById(userId)
		                          .orElseThrow(
				                          () -> new EntityNotFoundException("User not found with ID: " + userId)
		                          );

		deactivateUser(user);
	}

	@Transactional(readOnly = true)
	User getUserEntityById(long userId) {
		return userRepository.findById(userId)
		                     .orElseThrow(
				                     () -> new EntityNotFoundException("User not found with id: " + userId)
		                     );
	}


	private void deactivateUser(User user){
		String userEmail = String.format(
				"deleted_%s@quicktix.internal",
				UUID.randomUUID()
		);

		user.setEmail(userEmail);
		user.setPasswordHash(UUID.randomUUID().toString());
		user.setFirstName("Deleted");
		user.setLastName("User");
		user.setPhoneNumber(null);
		user.setActive(false);

		userRepository.save(user);
		log.info("User with ID {} deactivated", user.getId());
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
