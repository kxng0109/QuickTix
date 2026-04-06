package io.github.kxng0109.quicktix.service;

import io.github.kxng0109.quicktix.dto.response.DashboardMetricsResponse;
import io.github.kxng0109.quicktix.enums.BookingStatus;
import io.github.kxng0109.quicktix.enums.EventStatus;
import io.github.kxng0109.quicktix.enums.PaymentStatus;
import io.github.kxng0109.quicktix.repositories.BookingRepository;
import io.github.kxng0109.quicktix.repositories.EventRepository;
import io.github.kxng0109.quicktix.repositories.PaymentRepository;
import io.github.kxng0109.quicktix.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Exception thrown during registration or profile updates when a requested unique identifier
 * (such as an email address) is already claimed by another account.
 * <p>
 * Prevents unique constraint violations in the database and signals to the client that
 * they must provide alternative credentials or initiate a password reset.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

	private final PaymentRepository paymentRepository;
	private final BookingRepository bookingRepository;
	private final UserRepository userRepository;
	private final EventRepository eventRepository;

	/**
	 * Compiles the current state of the ticketing platform into a single metrics payload.
	 * <p>
	 * Automatically handles null safeguards for revenue calculations if the database contains
	 * no completed transactions.
	 * </p>
	 *
	 * @return A {@link DashboardMetricsResponse} containing total revenue, ticket sales, user counts, and active events.
	 */
	@Transactional(readOnly = true)
	public DashboardMetricsResponse getDashboardMetrics() {
		BigDecimal totalRevenue = Objects.requireNonNullElse(
				paymentRepository.calculateTotalRevenue(PaymentStatus.COMPLETED),
				BigDecimal.ZERO
		);

		long totalTicketSold = bookingRepository.countTicketsSold(BookingStatus.CONFIRMED);
		long totalActiveUsers = userRepository.countAllByIsActive(true);
		long totalActiveEvents = eventRepository.countAllByStatus(EventStatus.ONGOING);
		long totalUpcomingEvents = eventRepository.countAllByStatus(EventStatus.UPCOMING);

		return DashboardMetricsResponse.builder()
		                               .totalRevenue(totalRevenue)
		                               .totalTicketsSold(totalTicketSold)
		                               .totalActiveUsers(totalActiveUsers)
		                               .totalActiveEvents(totalActiveEvents)
		                               .totalUpcomingEvents(totalUpcomingEvents)
		                               .build();
	}
}
