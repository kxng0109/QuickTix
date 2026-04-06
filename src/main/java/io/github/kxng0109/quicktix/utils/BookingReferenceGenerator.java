package io.github.kxng0109.quicktix.utils;

import java.security.SecureRandom;

/**
 * Utility class for generating secure, human-readable booking reference codes.
 * <p>
 * Utilizes {@link java.security.SecureRandom} to generate cryptographically strong,
 * alphanumeric strings (e.g., "QT-A9K4P2"). The character pool explicitly excludes
 * visually ambiguous characters (like '1', 'I', 'O', '0') to prevent customer
 * confusion during ticket lookups.
 * </p>
 */
public class BookingReferenceGenerator {

	private static final String CHARACTERS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
	private static final int CODE_LENGTH = 6;
	private static final SecureRandom random = new SecureRandom();

	public static String generate() {
		StringBuilder sb = new StringBuilder("QT-");
		for (int i = 0; i < CODE_LENGTH; i++) {
			int index = random.nextInt(CHARACTERS.length());
			sb.append(CHARACTERS.charAt(index));
		}
		return sb.toString();
	}
}
