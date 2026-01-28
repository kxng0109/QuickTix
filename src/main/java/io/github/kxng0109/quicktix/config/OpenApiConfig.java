package io.github.kxng0109.quicktix.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI quickTixOpenAPI() {
		return new OpenAPI()
				.info(new Info()
						      .title("QuickTix API")
						      .description("""
								                   QuickTix is a comprehensive ticket booking system API for managing events, venues, seat reservations, bookings, and payments.
								                   
								                   ## Features
								                   - **User Management**: Create and manage user accounts
								                   - **Venue Management**: Create and manage event venues
								                   - **Event Management**: Create, update, and manage events with automatic seat generation
								                   - **Seat Reservation**: Hold and release seats with pessimistic locking to prevent double-booking
								                   - **Booking System**: Create and manage bookings with automatic expiration
								                   - **Payment Processing**: Initialize and verify payments with refund support
								                   
								                   ## Booking Flow
								                   1. User selects seats and holds them (15-minute hold period)
								                   2. User creates a pending booking with held seats
								                   3. User initializes payment
								                   4. Payment is verified through the gateway
								                   5. Booking is confirmed and seats are marked as booked
								                   
								                   ## Error Handling
								                   All errors return a consistent JSON structure with `statusCode`, `message`, `path`, and `timestamp` fields.
								                   """)
						      .version("1.0.0")
						      .contact(new Contact()
								               .name("Joshua Ike")
								               .url("https://github.com/kxng0109")
						      )
						      .license(new License()
								               .name("MIT License")
								               .url("https://opensource.org/licenses/MIT")
						      )
				)
				.servers(List.of(
						new Server()
								.url("http://localhost:8080")
								.description("Local development server")
				))
				.tags(List.of(
						new Tag().name("Users").description("User registration and management"),
						new Tag().name("Venues").description("Venue creation and management"),
						new Tag().name("Events").description("Event creation, scheduling, and management"),
						new Tag().name("Seats").description("Seat availability, holding, and releasing"),
						new Tag().name("Bookings").description("Booking creation, confirmation, and cancellation"),
						new Tag().name("Payments").description("Payment initialization, verification, and refunds")
				));
	}
}