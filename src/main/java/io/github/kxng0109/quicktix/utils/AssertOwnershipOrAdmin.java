package io.github.kxng0109.quicktix.utils;

import io.github.kxng0109.quicktix.entity.User;
import io.github.kxng0109.quicktix.enums.Role;
import org.springframework.security.access.AccessDeniedException;

public class AssertOwnershipOrAdmin {
	public static void check(User currentUser, User owner) throws AccessDeniedException {
		if (currentUser.getRole() == Role.ADMIN) return;

		if (!currentUser.getId().equals(owner.getId())) {
			throw new AccessDeniedException("Requested resource not found.");
		}
	}
}
