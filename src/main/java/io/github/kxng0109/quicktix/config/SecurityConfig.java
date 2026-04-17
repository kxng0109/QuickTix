package io.github.kxng0109.quicktix.config;

import io.github.kxng0109.quicktix.filter.HoldSeatRateLimiterFilter;
import io.github.kxng0109.quicktix.filter.UserRateLimiterFilter;
import io.github.kxng0109.quicktix.security.JwtAccessDeniedHandler;
import io.github.kxng0109.quicktix.security.JwtAuthenticationEntryPoint;
import io.github.kxng0109.quicktix.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configuration class for securing the application using Spring Security.
 *
 * <p>This class is responsible for defining security policies, authentication, and
 * authorization mechanisms for the application. It sets up JWT-based stateless
 * authentication, along with role-based access controls for various endpoints.
 * Additionally, it integrates security filters for JWT validation, rate limiting,
 * and exception handling.
 *
 * <p>The primary goal of this configuration is to ensure proper access management
 * to APIs, protect against unauthorized and invalid requests, and enforce separation
 * of user roles.
 *
 * <p>Key components used:
 * <ul>
 *     <li>{@link JwtAuthenticationFilter} for validating JWT tokens and user authentication.</li>
 *     <li>{@link JwtAuthenticationEntryPoint} for handling unauthorized access attempts.</li>
 *     <li>{@link JwtAccessDeniedHandler} for managing access denial scenarios.</li>
 *     <li>{@link UserRateLimiterFilter} and {@link HoldSeatRateLimiterFilter} for request rate limiting.</li>
 * </ul>
 *
 * <p>This class utilizes {@link EnableWebSecurity} and {@link EnableMethodSecurity} to
 * enable global and method-level security configurations. All configurations are designed
 * to enforce stateless session management and rely on bearer tokens for authentication.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
	private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
	private final UserRateLimiterFilter userRateLimiterFilter;
	private final HoldSeatRateLimiterFilter holdSeatRateLimiterFilter;

	@Bean
	SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.authorizeHttpRequests(authz -> authz
						.requestMatchers(
								"/api/v1/auth/**",
								"/swagger-ui/**",
								"/swagger-ui.html",
								"/v3/api-docs/**",
								"/api-docs/**",
								"/api/v1/webhooks/stripe",
								"/api/v1/webhooks/paystack"
						).permitAll()

						.requestMatchers(HttpMethod.GET, "/api/v1/events/**").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/venues/**").permitAll()

						.requestMatchers("/api/v1/bookings/**").hasAnyRole("USER", "ADMIN")
						.requestMatchers("/api/v1/users/me/**").hasAnyRole("USER", "ADMIN")

						.requestMatchers(HttpMethod.POST, "/api/v1/events/**").hasRole("ADMIN")
						.requestMatchers(HttpMethod.PUT, "/api/v1/events/**").hasRole("ADMIN")
						.requestMatchers(HttpMethod.PATCH, "/api/v1/events/**").hasRole("ADMIN")
						.requestMatchers(HttpMethod.DELETE, "/api/v1/events/**").hasRole("ADMIN")

						.requestMatchers(HttpMethod.POST, "/api/v1/venues/**").hasRole("ADMIN")
						.requestMatchers(HttpMethod.PUT, "/api/v1/venues/**").hasRole("ADMIN")
						.requestMatchers(HttpMethod.PATCH, "/api/v1/venues/**").hasRole("ADMIN")
						.requestMatchers(HttpMethod.DELETE, "/api/v1/venues/**").hasRole("ADMIN")

						.requestMatchers("/api/v1/internal/admin/**").hasRole("ADMIN")
						.anyRequest().authenticated()
				)
				.sessionManagement(
						session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
				)
				.exceptionHandling(
						exception -> exception
								.authenticationEntryPoint(jwtAuthenticationEntryPoint)
								.accessDeniedHandler(jwtAccessDeniedHandler)
				)
				.addFilterBefore(
						jwtAuthenticationFilter,
						UsernamePasswordAuthenticationFilter.class
				)
				.addFilterAfter(
						userRateLimiterFilter,
						JwtAuthenticationFilter.class
				)
				.addFilterAfter(
						holdSeatRateLimiterFilter,
						UserRateLimiterFilter.class
				);

		return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public AuthenticationManager authenticationManager(
			AuthenticationConfiguration authenticationConfiguration
	) throws Exception {
		return authenticationConfiguration.getAuthenticationManager();
	}
}
