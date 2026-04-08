package io.github.kxng0109.quicktix.enums;

/**
 * Defines the granted authorities and access levels within the Spring Security context.
 * <p>
 * Used heavily in conjunction with {@code @PreAuthorize} annotations on controllers
 * to enforce Role-Based Access Control (RBAC).
 * </p>
 */
public enum Role{
	/**
	 * Standard customer account. Can browse events, book seats, and view personal history.
	 */
	USER,

	/**
	 * Administrative account. Possesses global read/write privileges, including event creation,
	 * global dashboard access, and forced system overrides.
	 */
	ADMIN
}
