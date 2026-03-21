package io.github.kxng0109.quicktix.service;

import io.github.kxng0109.quicktix.entity.User;
import io.github.kxng0109.quicktix.enums.Role;
import io.github.kxng0109.quicktix.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CustomUserDetailsServiceTest {

	private final String email = "test@email.com";

	@Mock
	private UserRepository userRepository;

	@InjectMocks
	private CustomUserDetailsService userDetailsService;

	@Test
	void loadUserByUsername_shouldReturnUserDetails_whenUserExists() {
		User user = User.builder()
		                .id(100L)
		                .email(email)
		                .role(Role.USER)
		                .build();

		when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

		UserDetails result = userDetailsService.loadUserByUsername(email);

		assertNotNull(result);
		assertEquals(email, result.getUsername());
		assertEquals(List.of(new SimpleGrantedAuthority("ROLE_USER")), result.getAuthorities());
		verify(userRepository).findByEmail(email);
	}

	@Test
	public void loadUserByUsername_should_throwUsernameNotFoundException_when_userWithEmailDoesNotExist() {
		when(userRepository.findByEmail(anyString()))
				.thenReturn(Optional.empty());

		assertThrows(
				UsernameNotFoundException.class,
				() -> userDetailsService.loadUserByUsername(email)
		);

		verify(userRepository).findByEmail(anyString());
	}
}
