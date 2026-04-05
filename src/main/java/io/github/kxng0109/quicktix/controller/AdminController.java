package io.github.kxng0109.quicktix.controller;

import io.github.kxng0109.quicktix.dto.response.DashboardMetricsResponse;
import io.github.kxng0109.quicktix.service.*;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Internal Administrative Controller.
 * <p>
 * This controller serves as the entry point for all high-level, cross-domain operational overrides
 * and platform metrics. It is intentionally isolated from standard RESTful domain controllers.
 * <br><br>
 * <b>Security Posture:</b>
 * <ul>
 * <li>Requires strict {@code ROLE_ADMIN} authorization via JWT.</li>
 * <li>Completely cloaked from public API documentation via {@code @Hidden}.</li>
 * <li>Routed under {@code /internal/} to evade standard automated vulnerability scanners.</li>
 * </ul>
 * </p>
 */
@Slf4j
@Hidden
@RestController
@RequestMapping("/api/v1/internal/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

	private final EventService eventService;
	private final SeatService seatService;
	private final PaymentService paymentService;
	private final UserService userService;
	private final AdminDashboardService adminDashboardService;

	/**
	 * Forcibly deactivates a user account and scrambles their Personally Identifiable Information (PII).
	 * <p>
	 * This is a cross-domain destructive action that performs a GDPR-compliant soft-delete, ensuring
	 * historical financial records (bookings, payments) do not trigger database foreign key constraint violations.
	 * </p>
	 *
	 * @param userId The unique identifier of the user to be purged.
	 * @return 204 No Content upon successful deactivation.
	 */
	@DeleteMapping("/users/{userId}")
	public ResponseEntity<Void> forceDeleteUser(
			@PathVariable long userId
	){
		userService.forceDeleteUser(userId);
		return ResponseEntity.noContent().build();
	}

	/**
	 * Forcibly cancels an active or upcoming event and halts all further ticket sales.
	 * <p>
	 * Invoking this endpoint triggers a massive cross-domain cascade. It updates the event state,
	 * evicts the public cache, and fires asynchronous background events to the RabbitMQ listener
	 * to begin processing mass refunds for all confirmed bookings.
	 * </p>
	 *
	 * @param eventId The unique identifier of the event to cancel.
	 * @return 204 No Content upon successful cancellation initiation.
	 */
	@PatchMapping("/events/{eventId}/cancel")
	public ResponseEntity<Void> cancelEvent(
			@PathVariable long eventId
	){
		eventService.cancelEventById(eventId);
		log.info("Event with ID {} cancelled", eventId);
		return ResponseEntity.noContent().build();
	}

	/**
	 * Executes a manual, customer-support override to refund a specific completed booking.
	 * <p>
	 * This endpoint bypasses standard user-initiated refund rules. It communicates directly
	 * with the external payment gateway, updates the internal ledger, and delegates the release
	 * of the associated physical seats.
	 * </p>
	 *
	 * @param bookingId The unique identifier of the booking to refund.
	 * @return 204 No Content upon successful gateway refund and internal state update.
	 */
	@PatchMapping("/bookings/{bookingId}/force-refund")
	public ResponseEntity<Void> refundCustomer(
			@PathVariable long bookingId
	){
		paymentService.refundPayment(bookingId);
		return ResponseEntity.noContent().build();
	}

	/**
	 * Infrastructure override to release jammed seats from Redis memory.
	 * <p>
	 * If a network partition or application crash leaves a seat permanently locked in the {@code HELD} state
	 * without a corresponding active Redis lock, this endpoint allows an administrator to directly destroy
	 * the locks and reset the PostgreSQL rows back to {@code AVAILABLE}.
	 * </p>
	 *
	 * @param seatIds A JSON array containing the specific seat IDs to un-jam.
	 * @return 204 No Content upon successful infrastructure reset.
	 */
	@PostMapping("/seats/release")
	public ResponseEntity<Void> forceReleaseSeat(
			@RequestBody List<Long> seatIds
	){
		seatService.releaseSeats(seatIds);
		return ResponseEntity.noContent().build();
	}

	/**
	 * Retrieves high-level, aggregated platform metrics for the executive dashboard.
	 * <p>
	 * This endpoint executes highly optimized JPQL aggregation queries to calculate total revenue,
	 * user counts, and active events directly at the database level, completely avoiding Java memory overhead.
	 * </p>
	 *
	 * @return 200 OK containing the compiled {@link DashboardMetricsResponse}.
	 */
	@GetMapping("/dashboard")
	public ResponseEntity<DashboardMetricsResponse> getDashboardMetrics() {
		return ResponseEntity.ok(adminDashboardService.getDashboardMetrics());
	}
}
