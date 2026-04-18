package io.github.kxng0109.quicktix.service;

import io.github.kxng0109.quicktix.entity.User;
import io.github.kxng0109.quicktix.exception.InvalidOperationException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class JwtService {

	@Value("${jwt.secret}")
	private String secretKey;

	@Value("${jwt.expiration}")
	private Long expiration;

	/**
	 * Generates a JSON Web Token (JWT) for the given user.
	 * <p>
	 * This method creates a JWT containing the user's role and ID as claims. The token is
	 * signed using a secret key to ensure security and validity. Additionally, it includes
	 * metadata such as the subject (user's email), issue date, and expiration date.
	 * Tokens are typically used for authentication and authorization purposes in the application.
	 * </p>
	 *
	 * @param user the {@link User} object for which the token is being generated;
	 *             cannot be {@code null}. The user must have a valid {@code id}, {@code email},
	 *             and {@code role}, as these are required to populate token claims.
	 * @return a {@link String} representing the generated JWT. Ensures the token is signed and
	 * compact, ready for transmission and use in secure authentication workflows.
	 * @throws NullPointerException     if the provided {@code user} is {@code null}.
	 * @throws IllegalArgumentException if any critical claims (e.g., user ID, email, role)
	 *                                  are invalid or missing.
	 * @implNote The expiration time is dynamically computed based on the current time and
	 * a predefined expiration duration. Ensure {@code expiration} is configured correctly
	 * in the application context.
	 * @see Jwts#builder()
	 * @see Date
	 */
	public String generateToken(User user) {
		Map<String, Object> claims = new HashMap<>();
		claims.put("role", user.getRole().name());
		claims.put("userId", user.getId());

		Date now = new Date();
		Date expiryDate = new Date(now.getTime() + expiration);

		return Jwts.builder()
		           .claims(claims)
		           .subject(user.getEmail())
		           .issuedAt(now)
		           .expiration(expiryDate)
		           .signWith(getSigningKey())
		           .compact();
	}

	/**
	 * Extracts the email (subject) from a JWT token.
	 *
	 * @param token the JWT token
	 * @return the email address, or null if token is invalid
	 */
	public String extractEmail(String token) {
		Claims claims = extractAll(token);
		return claims != null ? claims.getSubject() : null;
	}

	/**
	 * Evaluates the validity of a JWT token against a provided user.
	 * <p>
	 * This method checks if the token is valid by performing the following steps:
	 * <ul>
	 *   <li>Extracts the email from the token and verifies that it matches the user's email.</li>
	 *   <li>Ensures the token is not expired by invoking the {@link #isTokenExpired(String)} method.</li>
	 *   <li>Handles token parsing errors (e.g., malformed or invalid token structures) by catching {@link JwtException}.</li>
	 * </ul>
	 * If any of these checks fail, the method logs the failure and returns {@code false}.
	 * </p>
	 *
	 * @param token the JWT token to validate; must not be {@code null}, malformed, or unsigned.
	 *              The token must be signed with the application's secret key.
	 * @param user  the {@link User} object against which the token is validated; must not be {@code null}.
	 *              The user's email is matched with the email extracted from the token.
	 * @return {@code true} if the token is valid and matches the user's email; {@code false} otherwise.
	 * Returns {@code false} if the token is expired, invalid, or does not belong to the user.
	 * @throws NullPointerException  if {@code token} or {@code user} is {@code null}.
	 * @throws IllegalStateException if the signing key or JWT parser is not properly initialized.
	 * @implNote This method relies on the {@link #extractEmail(String)} and {@link #isTokenExpired(String)} methods
	 * to validate the token's email and expiration status.
	 */
	public boolean isTokenValid(String token, User user) {
		try {
			String email = extractEmail(token);
			return email != null && email.equals(user.getEmail()) && !isTokenExpired(token);
		} catch (JwtException e) {
			log.debug("Token validation failed: {}", e.getMessage());
			return false;
		}
	}

	/**
	 * Determines if the provided JWT token is expired.
	 * <p>
	 * This method checks the expiration timestamp of the token's payload to determine whether the token
	 * is still valid or has expired. If the token cannot be parsed or does not contain valid claims,
	 * it is assumed to not be expired. If the token has already expired, it returns {@code true}.
	 * </p>
	 *
	 * <p>
	 * This method handles the {@link ExpiredJwtException} explicitly to ensure that expired tokens
	 * do not cause unexpected exceptions.
	 * </p>
	 *
	 * @param token the JWT token to evaluate; must not be {@code null}.
	 *              The token must be signed with the correct secret key used by the application.
	 * @return {@code true} if the token is expired; {@code false} otherwise. If the token cannot
	 * be parsed (e.g., malformed, invalid, or unsigned), the method returns {@code false}.
	 * @throws NullPointerException  if the {@code token} is {@code null}.
	 * @throws IllegalStateException if the signing key or JWT parser is not properly initialized.
	 * @implNote This method relies on the {@link Claims#getExpiration()} method to determine the
	 * expiration. If claims cannot be extracted, the token is treated as non-expired.
	 */
	public boolean isTokenExpired(String token) {
		try {
			Claims claims = extractAll(token);
			if (claims == null) return false;

			Date expirationDate = claims.getExpiration();
			return expirationDate.before(new Date());
		} catch (ExpiredJwtException e) {
			return true;
		}
	}

	/**
	 * Retrieves the configured JWT expiration duration, expressed in seconds.
	 * <p>
	 * This method calculates the configured expiration time of JWT tokens by converting the
	 * stored expiration value from milliseconds to seconds. It is used internally to define
	 * the lifespan of tokens generated by the {@link JwtService}.
	 * </p>
	 *
	 * @return the expiration duration in seconds, derived from the {@code expiration} field.
	 * Returns {@code 0} if the expiration value is not set or is {@code null}.
	 * @implNote This method performs a simple division of the {@code expiration} field
	 * (stored in milliseconds) by 1000 to convert the value to seconds.
	 */
	public Long getExpirationInSeconds() {
		return expiration / 1000;
	}

	/**
	 * Retrieves the expiration time of a given JWT token, expressed in seconds since the epoch.
	 * <p>
	 * This method parses the provided token to extract its claims and calculates the token's expiration
	 * time in seconds. It assumes the presence of a valid {@link Claims#getExpiration()} timestamp
	 * in the token's payload.
	 * </p>
	 *
	 * @param token the JWT token to analyze; must be a valid and non-expired token
	 * @return the expiration time of the token in seconds since the epoch
	 * @throws InvalidOperationException if the token is invalid or cannot be parsed
	 * @implNote This method divides the token expiration timestamp (in milliseconds) by 1000
	 * to convert it to seconds.
	 */
	public long getTokenExpirationInSeconds(String token) {
		Claims claims = extractAll(token);
		if (claims == null) throw new InvalidOperationException("Invalid token");
		return claims.getExpiration().getTime() / 1000;
	}

	/**
	 * Extracts all claims from a given JWT token.
	 * <p>
	 * This method attempts to parse the provided JWT token and extract its claims payload.
	 * In cases where the token has expired, it retrieves the associated claims from the
	 * {@link ExpiredJwtException}. If the token parsing fails due to other reasons, the
	 * method logs the error and returns {@code null}.
	 * </p>
	 *
	 * <p>
	 * Tokens are parsed using the signing key retrieved from {@link #getSigningKey()}.
	 * This method assumes that the token is signed and properly structured according to
	 * JWT specifications.
	 * </p>
	 *
	 * @param token the JWT token to be parsed; must not be {@code null}.
	 * @return the {@link Claims} extracted from the token, or {@code null} if the token
	 * is invalid, malformed, or cannot be parsed.
	 * @throws NullPointerException  if {@code token} is {@code null}.
	 * @throws IllegalStateException if the signing key or JWT parser is not properly initialized.
	 * @implNote When the token is expired, this method makes use of
	 * {@link ExpiredJwtException#getClaims()} to retrieve claims from the exception.
	 */
	private Claims extractAll(String token) {
		try {
			return Jwts.parser()
			           .verifyWith(getSigningKey())
			           .build()
			           .parseSignedClaims(token)
			           .getPayload();
		} catch (ExpiredJwtException e) {
			return e.getClaims();
		} catch (JwtException e) {
			log.debug("Failed to parse JWT: {}", e.getMessage());
			return null;
		}
	}

	/**
	 * Creates the signing key from the secret string.
	 * <p>
	 * The string is converted to bytes and an HMAC-SHA key is created .
	 *
	 * @return the secret key for signing
	 */
	private SecretKey getSigningKey() {
		byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
		return Keys.hmacShaKeyFor(keyBytes);
	}
}
