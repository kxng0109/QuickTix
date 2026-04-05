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

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

	private final PaymentRepository paymentRepository;
	private final BookingRepository bookingRepository;
	private final UserRepository userRepository;
	private final EventRepository eventRepository;

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
