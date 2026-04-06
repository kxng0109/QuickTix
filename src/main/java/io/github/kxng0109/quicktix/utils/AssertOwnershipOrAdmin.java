package io.github.kxng0109.quicktix.utils;

import io.github.kxng0109.quicktix.entity.User;
import io.github.kxng0109.quicktix.enums.Role;
import org.springframework.security.access.AccessDeniedException;

/**
 * Security utility for enforcing resource ownership rules.
 * <p>
 * Provides centralized validation to ensure that a user attempting to access or modify
 * a specific entity is either the rightful owner of that entity or possesses system-wide
 * administrative privileges ({@code ROLE_ADMIN}).
 * </p>
 */
public class AssertOwnershipOrAdmin {

	/**
	 * Validates access rights for a specific resource.
	 *
	 * @param currentUser The authenticated user attempting the action.
	 * @param owner       The actual user entity that owns the requested resource.
	 * @throws AccessDeniedException if the current user is neither the owner nor an administrator.
	 */
	public static void check(User currentUser, User owner) throws AccessDeniedException {
		if (currentUser.getRole() == Role.ADMIN) return;

		if (!currentUser.getId().equals(owner.getId())) {
			throw new AccessDeniedException("Requested resource not found.");
		}
	}
}
