package io.github.kxng0109.quicktix.exception;

import io.github.kxng0109.quicktix.dto.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(UserExistsException.class)
    public ResponseEntity<ErrorResponse> handleUserExistsException(
            UserExistsException ex,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.CONFLICT;
        ErrorResponse response = ErrorResponse
                .builder()
                .timestamp(OffsetDateTime.now())
                .statusCode(status.value())
                .error(status.getReasonPhrase())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();

        return new ResponseEntity<>(response, status);
    }
}
