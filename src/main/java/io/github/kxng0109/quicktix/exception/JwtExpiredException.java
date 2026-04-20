package io.github.kxng0109.quicktix.exception;

public class JwtExpiredException extends RuntimeException {
	public JwtExpiredException(String message) {
		super(message);
	}
}
