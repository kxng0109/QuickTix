package io.github.kxng0109.quicktix.service;

import io.github.kxng0109.quicktix.dto.request.CreateUserRequest;
import io.github.kxng0109.quicktix.dto.request.LoginRequest;
import io.github.kxng0109.quicktix.dto.response.AuthResponse;
import io.github.kxng0109.quicktix.entity.User;
import io.github.kxng0109.quicktix.enums.Role;
import io.github.kxng0109.quicktix.exception.UserExistsException;
import io.github.kxng0109.quicktix.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

	private final JwtService jwtService;
	private final AuthenticationManager authenticationManager;
	private final PasswordEncoder passwordEncoder;
	private final UserRepository userRepository;


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
		                .role(Role.USER) //Um, is this actually needed?
		                .build();

		User newUser = userRepository.save(user);
		return buildAuthResponse(newUser);
	}

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
