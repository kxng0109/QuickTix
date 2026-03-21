package io.github.kxng0109.quicktix.service;

import io.github.kxng0109.quicktix.entity.User;
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
	 * Checks if a token is expired.
	 *
	 * @param token the JWT token
	 * @return true if expired
	 */
	public boolean isTokenExpired(String token) {
		try {
			Claims claims = extractAll(token);
			if (claims == null) return false;

			Date expirationDate = claims.getExpiration();
			return expirationDate.before(new Date());
		} catch (ExpiredJwtException e) {
			return false;
		}
	}

	/**
	 * Returns the token expiration time in seconds.
	 *
	 * @return expiration time in seconds
	 */
	public Long getExpirationInSeconds() {
		return expiration / 1000;
	}

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
