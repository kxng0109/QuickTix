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

/**
 * Service responsible for user profile management and secure account deletion.
 * <p>
 * Enforces strict authorization checks using {@link AssertOwnershipOrAdmin} to guarantee
 * that users can only view or modify their own data, while preserving administrative override capabilities.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	/**
	 * Retrieves a user's profile by their unique ID.
	 *
	 * @param id The ID of the user.
	 * @return A safe {@link UserResponse} DTO without sensitive security fields.
	 * @throws EntityNotFoundException if the user is not found.
	 */
	@Transactional(readOnly = true)
	public UserResponse getUserById(long id) {
		User user = userRepository.findById(id)
		                          .orElseThrow(
				                          () -> new EntityNotFoundException("User not found with id: " + id)
		                          );

		return buildUserResponse(user);
	}

	/**
	 * Converts an authenticated {@link User} entity into a public-facing response DTO.
	 *
	 * @param currentUser The currently authenticated user from the Security Context.
	 * @return The user's profile data.
	 */
	@Transactional(readOnly = true)
	public UserResponse getUser(User currentUser) {
		return buildUserResponse(currentUser);
	}

	/**
	 * Updates the profile of an existing user. Called by the user.
	 * <p>
	 * Applies conditional updates to the user's password (if provided) by securely re-hashing it,
	 * and guarantees email uniqueness across the platform.
	 * </p>
	 *
	 * @param currentUser The authenticated user performing the action (checked for ownership/admin).
	 * @return The updated {@link UserResponse}.
	 * @throws UserExistsException if the new email is already registered to another user.
	 */
	@Transactional
	public UserResponse updateUser(CreateUserRequest request, User currentUser){
		return updateUserById(currentUser.getId(), request, currentUser);
	}

	/**
	 * Updates the profile of an existing user. Called directly by the admin.
	 * <p>
	 * Applies conditional updates to the user's password (if provided) by securely re-hashing it,
	 * and guarantees email uniqueness across the platform.
	 * </p>
	 *
	 * @param userId      The ID of the user to update.
	 * @param request     The payload containing updated names, email, or passwords.
	 * @param currentUser The authenticated user performing the action (checked for ownership/admin).
	 * @return The updated {@link UserResponse}.
	 * @throws UserExistsException if the new email is already registered to another user.
	 */
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

	/**
	 * Soft-deletes a user account initiated by the user themselves.
	 * <p>
	 * <b>Constraint:</b> If the user has active or historical bookings, this method throws a
	 * {@link ResourceInUseException} to prevent the destruction of active financial paths.
	 * Support/Admin intervention is required to force-delete accounts with financial histories.
	 * </p>
	 *
	 * @param currentUser The authenticated user requesting account deletion.
	 * @throws ResourceInUseException if the user has existing bookings.
	 */
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


	/**
	 * INTERNAL USE ONLY.
	 * Executes the core logic for a GDPR-compliant soft deletion of a user account.
	 * <p>
	 * Scrambles all Personally Identifiable Information (PII) using random UUIDs and static placeholders,
	 * destroys the password hash to mathematically prevent future authentication, and flags the account
	 * as inactive to sever Spring Security access.
	 * </p>
	 *
	 * @param user The {@link User} entity to be irreversibly deactivated.
	 */
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
