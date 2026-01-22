package io.github.kxng0109.quicktix.exception;

import io.github.kxng0109.quicktix.dto.exception.ErrorResponse;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
	@ExceptionHandler(UserExistsException.class)
	public ResponseEntity<ErrorResponse> handleUserExistsException(
			UserExistsException ex,
			HttpServletRequest request
	) {
		HttpStatus status = HttpStatus.CONFLICT;

		return buildErrorResponse(ex, request, status);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
			IllegalArgumentException ex,
			HttpServletRequest request
	) {
		HttpStatus status = HttpStatus.BAD_REQUEST;

		return buildErrorResponse(ex, request, status);
	}

	@ExceptionHandler(EntityNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleEntityNotFoundException(
			EntityNotFoundException ex,
			HttpServletRequest request
	) {
		HttpStatus status = HttpStatus.NOT_FOUND;

		return buildErrorResponse(ex, request, status);
	}

	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	public ResponseEntity<ErrorResponse> handleObjectOptimisticLockingFailure(
			ObjectOptimisticLockingFailureException ex,
			HttpServletRequest request
	) {
		HttpStatus status = HttpStatus.CONFLICT;
		return buildErrorResponse(ex, request, status);
	}

	@ExceptionHandler(ResourceInUseException.class)
	public ResponseEntity<ErrorResponse> handleResourceInUseException(
			ResourceInUseException ex,
			HttpServletRequest request
	) {
		HttpStatus status = HttpStatus.CONFLICT;
		return buildErrorResponse(ex, request, status);
	}

	@ExceptionHandler(InvalidOperationException.class)
	public ResponseEntity<ErrorResponse> handleInvalidOperationException(
			InvalidOperationException ex,
			HttpServletRequest request
	) {
		HttpStatus status = HttpStatus.BAD_REQUEST;
		return buildErrorResponse(ex, request, status);
	}

	@ExceptionHandler(InvalidAmountException.class)
	public ResponseEntity<ErrorResponse> handleInvalidAmountException(
			InvalidAmountException ex,
			HttpServletRequest request
	) {
		HttpStatus status = HttpStatus.BAD_REQUEST;
		return buildErrorResponse(ex, request, status);
	}

	@ExceptionHandler(PaymentFailedException.class)
	public ResponseEntity<ErrorResponse> handlePaymentFailedException(
			PaymentFailedException ex,
			HttpServletRequest request
	) {
		HttpStatus status = HttpStatus.BAD_REQUEST;
		return buildErrorResponse(ex, request, status);
	}

	/**
	 * Handles @Valid failures on DTOs (e.g. @RequestBody).
	 * Returns a map of field names to error messages.
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, String>> handleValidationExceptions(
			MethodArgumentNotValidException ex
	) {
		Map<String, String> errors = new HashMap<>();

		ex.getBindingResult().getAllErrors().forEach((error) -> {
			String fieldName = ((FieldError) error).getField();
			String errorMessage = error.getDefaultMessage();
			errors.put(fieldName, errorMessage);
		});

		return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
	}

	/**
	 * Handles @Validated failures on path/query parameters (e.g. @RequestParam @Min(1)).
	 * Returns a structured error object similar to your other handlers.
	 */
	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ErrorResponse> handleConstraintViolation(
			ConstraintViolationException ex,
			HttpServletRequest request
	) {
		HttpStatus status = HttpStatus.BAD_REQUEST;

		// ConstraintViolationException messages can be long/ugly, so we just pass the raw message
		return buildErrorResponse(ex, request, status);
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ErrorResponse> handleNoResourceFoundException(
			NoResourceFoundException ex,
			HttpServletRequest request
	){
		HttpStatus status = HttpStatus.NOT_FOUND;
		return buildErrorResponse(ex, request, status);
	}


	private ResponseEntity<ErrorResponse> buildErrorResponse(
			Exception ex,
			HttpServletRequest request,
			HttpStatus status
	) {
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
