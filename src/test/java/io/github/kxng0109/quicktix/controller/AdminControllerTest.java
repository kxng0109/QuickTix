package io.github.kxng0109.quicktix.controller;

import io.github.kxng0109.quicktix.config.SecurityConfig;
import io.github.kxng0109.quicktix.dto.response.DashboardMetricsResponse;
import io.github.kxng0109.quicktix.security.JwtAccessDeniedHandler;
import io.github.kxng0109.quicktix.security.JwtAuthenticationEntryPoint;
import io.github.kxng0109.quicktix.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
public class AdminControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private EventService eventService;

	@MockitoBean
	private SeatService seatService;

	@MockitoBean
	private PaymentService paymentService;

	@MockitoBean
	private UserService userService;

	@MockitoBean
	private AdminDashboardService adminDashboardService;

	@MockitoBean
	private JwtService jwtService;

	@MockitoBean
	private CustomUserDetailsService userDetailsService;

	private static final String BASE_URL = "/api/v1/internal/admin";

	@Test
	public void forceDeleteUser_shouldReturn204_whenCalledByAdmin() throws Exception {
		doNothing().when(userService).forceDeleteUser(anyLong());

		mockMvc.perform(delete(BASE_URL + "/users/{userId}", 100L)
				                .with(user("admin@test.com").roles("ADMIN")))
		       .andExpect(status().isNoContent());
	}

	@Test
	public void cancelEvent_shouldReturn204_whenCalledByAdmin() throws Exception {
		doNothing().when(eventService).cancelEventById(anyLong());

		mockMvc.perform(patch(BASE_URL + "/events/{eventId}/cancel", 50L)
				                .with(user("admin@test.com").roles("ADMIN")))
		       .andExpect(status().isNoContent());
	}

	@Test
	public void forceRefundCustomer_shouldReturn204_whenCalledByAdmin() throws Exception {
		doNothing().when(paymentService).refundPayment(anyLong());

		mockMvc.perform(patch(BASE_URL + "/bookings/{bookingId}/force-refund", 200L)
				                .with(user("admin@test.com").roles("ADMIN")))
		       .andExpect(status().isNoContent());
	}

	@Test
	public void clearStuckSeats_shouldReturn204_whenCalledByAdmin() throws Exception {
		List<Long> seatIds = List.of(1L, 2L, 3L);
		doNothing().when(seatService).releaseSeats(anyList());

		mockMvc.perform(post(BASE_URL + "/seats/release")
				                .with(user("admin@test.com").roles("ADMIN"))
				                .contentType(MediaType.APPLICATION_JSON)
				                .content(objectMapper.writeValueAsString(seatIds)))
		       .andExpect(status().isNoContent());
	}

	@Test
	public void getDashboardMetrics_shouldReturn200AndMetrics_whenCalledByAdmin() throws Exception {
		DashboardMetricsResponse mockResponse = DashboardMetricsResponse.builder()
		                                                                .totalRevenue(new BigDecimal("150000.00"))
		                                                                .totalTicketsSold(5000L)
		                                                                .totalActiveUsers(1200L)
		                                                                .totalActiveEvents(45L)
		                                                                .totalUpcomingEvents(32L)
		                                                                .build();

		when(adminDashboardService.getDashboardMetrics())
				.thenReturn(mockResponse);

		mockMvc.perform(get(BASE_URL + "/dashboard")
				                .with(user("admin@test.com").roles("ADMIN")))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("$.totalRevenue").value(150000.00))
		       .andExpect(jsonPath("$.totalTicketsSold").value(5000))
		       .andExpect(jsonPath("$.totalActiveUsers").value(1200))
		       .andExpect(jsonPath("$.totalActiveEvents").value(45))
		       .andExpect(jsonPath("$.totalUpcomingEvents").value(32L));
	}
}
