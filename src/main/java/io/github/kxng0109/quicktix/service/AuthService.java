package io.github.kxng0109.quicktix.service;

import io.github.kxng0109.quicktix.dto.request.CreateUserRequest;
import io.github.kxng0109.quicktix.dto.request.LoginRequest;
import io.github.kxng0109.quicktix.dto.response.AuthResponse;
import io.github.kxng0109.quicktix.entity.User;
import io.github.kxng0109.quicktix.enums.Role;
import io.github.kxng0109.quicktix.exception.InvalidOperationException;
import io.github.kxng0109.quicktix.exception.UserExistsException;
import io.github.kxng0109.quicktix.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Service responsible for user identity and access management.
 * <p>
 * Handles the registration of new users and the authentication of existing users,
 * delegating credential verification to Spring Security's {@link AuthenticationManager}
 * and token generation to the {@link JwtService}.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class AuthService {

	private final JwtService jwtService;
	private final AuthenticationManager authenticationManager;
	private final PasswordEncoder passwordEncoder;
	private final UserRepository userRepository;
	private final StringRedisTemplate stringRedisTemplate;


	/**
	 * Registers a new user in the system.
	 * <p>
	 * Verifies that the email is not already in use, securely hashes the user's password,
	 * and assigns the default {@link Role#USER} role. The account is activated by default.
	 * </p>
	 *
	 * @param request The DTO containing the user's registration details.
	 * @return An {@link AuthResponse} containing the generated JWT token and user details.
	 * @throws UserExistsException if a user with the provided email already exists.
	 */
	@Transactional
	public AuthResponse handleRegistration(CreateUserRequest request) {
		if (userRepository.existsByEmail(request.email())) {
			throw new UserExistsException();
		}

		String phoneNumber = request.phoneNumber() != null ? request.phoneNumber() : null;

		User user = User.builder()
		                .firstName(request.firstName())
		                .lastName(request.lastName())
		                .email(request.email())
		                .passwordHash(passwordEncoder.encode(request.password()))
		                .phoneNumber(phoneNumber)
		                .role(Role.USER)
		                .isActive(true)
		                .build();

		User newUser = userRepository.save(user);
		return buildAuthResponse(newUser);
	}

	/**
	 * Authenticates a user's login credentials.
	 * <p>
	 * Leverages Spring Security's AuthenticationManager to securely compare the provided
	 * password against the stored bcrypt hash. If successful, generates a new JWT token.
	 * </p>
	 *
	 * @param request The DTO containing the user's email and plaintext password.
	 * @return An {@link AuthResponse} containing the new JWT token and user details.
	 * @throws org.springframework.security.authentication.BadCredentialsException if the password does not match.
	 */
	@Transactional(readOnly = true)
	public AuthResponse handleLogin(LoginRequest request) {
		UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
				request.email(),
				request.password()
		);

		Authentication authentication = authenticationManager.authenticate(authToken);

		User user = (User) authentication.getPrincipal();

		return buildAuthResponse(user);
	}

	public void handleLogout(String token, User currentUser) {
		if (token == null || !token.startsWith("Bearer ") || !jwtService.isTokenValid(token.substring(7), currentUser)) {
			throw new InvalidOperationException("Invalid token!");
		}
		token = token.substring(7);

		long ttlSeconds = jwtService.getTokenExpirationInSeconds(token) - (System.currentTimeMillis() / 1000);

		if(ttlSeconds <= 0) {
			throw new InvalidOperationException("Token is already expired!");
		}

		boolean isBlacklisted = Objects.equals(
				Boolean.TRUE,
				stringRedisTemplate.opsForValue()
				                   .setIfAbsent(
						                   "blacklist:" + token,
						                   OffsetDateTime.now().toString(),
						                   Duration.ofSeconds(ttlSeconds)
				                   )
		);

		if(!isBlacklisted) throw new InvalidOperationException("Token previously blacklisted!");
	}

	private AuthResponse buildAuthResponse(User user) {
		String token = jwtService.generateToken(user);
		Long expiresIn = jwtService.getExpirationInSeconds();

		return AuthResponse.builder()
		                   .token(token)
		                   .tokenType("Bearer")
		                   .expiresIn(expiresIn)
		                   .email(user.getEmail())
		                   .role(user.getRole().name())
		                   .build();
	}
}
