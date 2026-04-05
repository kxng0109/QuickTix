package io.github.kxng0109.quicktix.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
@Schema(description = "Aggregated global metrics for the Admin Dashboard")
public record DashboardMetricsResponse(
		BigDecimal totalRevenue,
		long totalTicketsSold,
		long totalActiveUsers,
		long totalActiveEvents,
		long totalUpcomingEvents
) {
}
