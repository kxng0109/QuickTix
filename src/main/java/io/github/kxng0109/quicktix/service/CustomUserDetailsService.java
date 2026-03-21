package io.github.kxng0109.quicktix.service;

import io.github.kxng0109.quicktix.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Custom implementation of Spring Security's {@link org.springframework.security.core.userdetails.UserDetailsService UserDetailsService}.
 * <p>
 * This service is the bridge between Spring Security and our User entity.
 * When a user attempts to authenticate, Spring Security calls this service
 * to load the user's details from the database.
 * <p>
 * The flow:
 * <ol>
 * <li>User submits email + password to /auth/login</li>
 * <li>AuthenticationManager is invoked</li>
 * <li>AuthenticationManager calls this service's loadUserByUsername(email)</li>
 * <li>We fetch the User from the database</li>
 * <li>Spring Security compares the submitted password with the stored hash</li>
 * <li>If valid, authentication succeeds</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements org.springframework.security.core.userdetails.UserDetailsService {

	private final UserRepository userRepository;


	/**
	 * Loads a user by their email address.
	 * <p>
	 * Despite the method name "loadUserByUsername", we use email as the
	 * unique identifier. The username in Spring Security is whatever
	 * unique identifier you use for login.
	 *
	 * @param email the username identifying the user whose data is required.
	 * @return a fully populated user record
	 * <p>(our {@link io.github.kxng0109.quicktix.entity.User User} implements {@link UserDetails}) (never <code>null</code>)</p>
	 * @throws UsernameNotFoundException if the user could not be found or the user has no
	 *                                   GrantedAuthority
	 * @see io.github.kxng0109.quicktix.entity.User User
	 */
	@Override
	public UserDetails loadUserByUsername(@NonNull String email) throws UsernameNotFoundException {
		return userRepository.findByEmail(email)
		                     .orElseThrow(
				                     () -> new UsernameNotFoundException(
						                     String.format("User with email %s not found!", email)
				                     )
		                     );
	}
}
