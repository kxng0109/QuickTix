package io.github.kxng0109.quicktix.config;

import io.github.kxng0109.quicktix.entity.Event;
import io.github.kxng0109.quicktix.entity.Seat;
import io.github.kxng0109.quicktix.entity.User;
import io.github.kxng0109.quicktix.entity.Venue;
import io.github.kxng0109.quicktix.enums.EventStatus;
import io.github.kxng0109.quicktix.enums.Role;
import io.github.kxng0109.quicktix.enums.SeatStatus;
import io.github.kxng0109.quicktix.repositories.EventRepository;
import io.github.kxng0109.quicktix.repositories.SeatRepository;
import io.github.kxng0109.quicktix.repositories.UserRepository;
import io.github.kxng0109.quicktix.repositories.VenueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
@Profile({"mock-stress-test", "dev"})
public class DataSeeder implements CommandLineRunner {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final VenueRepository venueRepository;
	private final EventRepository eventRepository;
	private final SeatRepository seatRepository;

	@Override
	public void run(String... args) {
// Automatically create an admin user on startup if one doesn't exist
		if (userRepository.findByEmail("admin@quicktix.com").isEmpty()) {
			User admin = User.builder()
			                 .firstName("System")
			                 .lastName("Admin")
			                 .email("admin@quicktix.com")
			                 .passwordHash(passwordEncoder.encode("Password123!"))
			                 .role(Role.ADMIN)
			                 .build();

			userRepository.save(admin);
			log.info("System Admin account seeded successfully.");
		}

		// 2. Seed Venue, Event, and Seats
		if (venueRepository.count() == 0) {
			// Create a Venue
			Venue venue = Venue.builder()
			                   .name("Eko Convention Center")
			                   .address("Adetokunbo Ademola Street")
			                   .city("Lagos")
			                   .totalCapacity(5000)
			                   .build();
			venue = venueRepository.save(venue);

			// Create an Event
			Event event = Event.builder()
			                   .name("Lagos Tech Fest 2026")
			                   .description("The biggest technology conference and exhibition in West Africa.")
			                   .venue(venue)
			                   .eventStartDateTime(Instant.now().plus(14, ChronoUnit.DAYS))
			                   .eventEndDateTime(Instant.now().plus(14, ChronoUnit.DAYS).plus(8, ChronoUnit.HOURS))
			                   .ticketPrice(BigDecimal.valueOf(15000))
			                   .status(EventStatus.UPCOMING)
			                   .build();
			event = eventRepository.save(event);

			// Generate 50 Available Seats for this Event
			List<Seat> seats = new ArrayList<>();
			for (int i = 1; i <= 10; i++) {
				seats.add(Seat.builder()
				              .seatNumber(i)
				              .rowName("A")
				              .seatStatus(SeatStatus.AVAILABLE)
				              .event(event)
				              .build());
			}
			seatRepository.saveAll(seats);

			log.info("Venue (Eko Convention Center), Event (Lagos Tech Fest 2026), and 50 Seats seeded successfully.");
		}
	}
}
