package io.github.kxng0109.quicktix.exception;

public class UserExistsException extends RuntimeException {
    public UserExistsException(){
        super("User with this email exists.");
    }

    public UserExistsException(String message) {
        super(message);
    }
}
