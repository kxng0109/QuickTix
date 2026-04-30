package io.github.kxng0109.quicktix.config;

import io.github.kxng0109.quicktix.entity.*;
import io.github.kxng0109.quicktix.enums.EventStatus;
import io.github.kxng0109.quicktix.enums.Role;
import io.github.kxng0109.quicktix.enums.SeatStatus;
import io.github.kxng0109.quicktix.repositories.*;
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
	private final SectionRepository sectionRepository; // ADDED
	private final RowRepository rowRepository;         // ADDED
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

		// 2. Seed Venue, Event, Sections, Rows, and Seats
		if (venueRepository.count() == 0) {
			// Level 0: Create a Venue
			Venue venue = Venue.builder()
			                   .name("Eko Convention Center")
			                   .address("Adetokunbo Ademola Street")
			                   .city("Lagos")
			                   .totalCapacity(5000)
			                   .build();
			venue = venueRepository.save(venue);

			// Level 1: Create an Event (Notice: NO ticketPrice here anymore)
			Event event = Event.builder()
			                   .name("Lagos Tech Fest 2026")
			                   .description("The biggest technology conference and exhibition in West Africa.")
			                   .venue(venue)
			                   .eventStartDateTime(Instant.now().plus(14, ChronoUnit.DAYS))
			                   .eventEndDateTime(Instant.now().plus(14, ChronoUnit.DAYS).plus(8, ChronoUnit.HOURS))
			                   .status(EventStatus.UPCOMING)
			                   .build();
			event = eventRepository.save(event);

			// Level 2: Create Sections (Prices belong here now)
			Section vipSection = Section.builder()
			                            .name("VIP")
			                            .description("Premium Front Seating")
			                            .capacity(20)
			                            .price(BigDecimal.valueOf(25000))
			                            .event(event)
			                            .build();

			Section generalSection = Section.builder()
			                                .name("General Admission")
			                                .description("Standard Seating")
			                                .capacity(30)
			                                .price(BigDecimal.valueOf(10000))
			                                .event(event)
			                                .build();

			List<Section> savedSections = sectionRepository.saveAll(List.of(vipSection, generalSection));

			// Level 3: Create Rows
			Row vipRowA = Row.builder().name("A").rowOrder(1).section(savedSections.get(0)).build();
			Row vipRowB = Row.builder().name("B").rowOrder(2).section(savedSections.get(0)).build();

			Row genRowC = Row.builder().name("C").rowOrder(3).section(savedSections.get(1)).build();
			Row genRowD = Row.builder().name("D").rowOrder(4).section(savedSections.get(1)).build();
			Row genRowE = Row.builder().name("E").rowOrder(5).section(savedSections.get(1)).build();

			List<Row> savedRows = rowRepository.saveAll(List.of(vipRowA, vipRowB, genRowC, genRowD, genRowE));

			// Level 4: Generate 50 Seats (10 per row)
			List<Seat> seats = new ArrayList<>();
			for (Row row : savedRows) {
				for (int i = 1; i <= 10; i++) {
					seats.add(Seat.builder()
					              .seatNumber(i)
					              .seatStatus(SeatStatus.AVAILABLE)
					              .event(event)
					              .row(row)
					              .price(row.getSection().getPrice())
					              .build());
				}
			}

			// Batch insert all 50 seats at once
			seatRepository.saveAll(seats);

			log.info("Venue, Event, Sections, Rows, and 50 spatial Seats seeded successfully.");
		}
	}
}