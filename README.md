# QuickTix

QuickTix is a comprehensive ticket booking system built with Spring Boot that demonstrates advanced concurrency control, transaction management, and scheduled task execution. The application handles real-world challenges in seat reservation systems, including race conditions during booking, payment processing, and automated resource management.

## Table of Contents

- [Overview](#overview)
- [Key Features](#key-features)
- [Technical Highlights](#technical-highlights)
- [Architecture](#architecture)
- [Database Schema](#database-schema)
- [Concurrency Management](#concurrency-management)
- [Scheduled Tasks](#scheduled-tasks)
- [Event-Driven Architecture](#event-driven-architecture)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [Technologies Used](#technologies-used)
- [Upcoming Features](#upcoming-features)

## Overview

QuickTix is a backend service that powers ticket booking operations for events such as concerts, movies, and shows. Users can browse events, select and temporarily hold seats, complete bookings with payment verification, and manage their reservations.  The system ensures data consistency through sophisticated locking mechanisms and handles edge cases like expired holds, pending bookings, and event cancellations.

## Key Features

### Event Management
- Create and manage venues with configurable seating capacity
- Schedule events with defined start and end times
- Track event status throughout their lifecycle (upcoming, ongoing, completed, cancelled)
- Automatic seat generation when events are created
- Dynamic event status updates based on time

### Seat Reservation System
- Browse available seats for any event
- Temporarily hold seats with a 15-minute expiration window
- Prevent double-booking through optimistic and pessimistic locking strategies
- Automatic release of expired seat holds
- Real-time seat availability tracking

### Booking Workflow
- Create pending bookings linked to held seats
- Confirm bookings only after successful payment verification
- Cancel bookings with automatic seat release
- Generate unique booking references for easy lookup
- Support for multiple seats per booking

### Payment Processing
- Initialize payments with amount validation
- Verify payments through external gateway integration
- Process refunds for cancelled bookings
- Handle payment state transitions (pending, completed, failed, refunded)
- Automatic booking confirmation upon successful payment

### User Management
- Create and manage user profiles
- Track booking history per user
- Prevent deletion of users with existing bookings
- Email-based user identification

## Technical Highlights

### Concurrency Control

QuickTix implements both **optimistic** and **pessimistic locking** strategies to handle concurrent seat reservations:

**Optimistic Locking**: Uses version fields on entities to detect conflicts at commit time. When two users attempt to modify the same seat, the second transaction receives an exception and can retry or inform the user.  This approach is suitable for low-contention scenarios and avoids holding database locks during processing.

**Pessimistic Locking**: Acquires exclusive row locks during the SELECT operation, preventing other transactions from accessing those rows until the lock is released. This is implemented in critical paths like seat holding, where high contention is expected.  The system uses `FOR UPDATE` clauses to guarantee that once a user begins reserving seats, no other user can interfere.

You can review the locking implementations in `SeatRepository.java` and `SeatService.java`.

### Transaction Management

The application leverages Spring's `@Transactional` annotation extensively to ensure atomic operations.  Key transaction patterns include:

- **Read-only transactions** for query operations to optimize performance
- **Multi-step transactional workflows** where payment verification, seat status updates, and booking confirmation must all succeed or all fail together
- **Transaction propagation** with `Propagation.REQUIRES_NEW` for independent refund processing, ensuring one failed refund doesn't rollback others
- **Isolation level management** to prevent dirty reads and ensure consistency

See `BookingService.java`, `PaymentService.java`, and related service classes for transaction usage patterns.

## Architecture

QuickTix follows a layered architecture pattern:

**Entities**: Domain models representing Users, Venues, Events, Seats, Bookings, and Payments with JPA mappings and relationships.

**Repositories**: Spring Data JPA repositories with custom query methods, including methods with pessimistic locking, fetch joins to avoid N+1 queries, and JPQL for complex queries.

**Services**: Business logic layer handling workflows, validations, and orchestration.  Services are transactional and coordinate between multiple repositories.

**DTOs**: Request and response objects separate internal entities from API contracts, with validation annotations ensuring data integrity.

**Exception Handling**: Global exception handler provides consistent error responses across the application with appropriate HTTP status codes.

**Scheduled Tasks**: Background jobs handle time-sensitive operations like releasing expired holds, updating event statuses, and retrying failed refunds.

**Event-Driven Components**: Application events and listeners enable asynchronous processing of complex workflows like event cancellations with mass refunds.

## Database Schema

The system uses six core entities with the following relationships:

**User** ↔ **Booking**:  One user can have many bookings.  Each booking belongs to one user.

**Venue** ↔ **Event**: One venue can host many events. Each event occurs at one venue.

**Event** ↔ **Seat**: One event has many seats. Each seat belongs to one event.  Seats are automatically generated when an event is created and cascade-deleted with the event.

**Event** ↔ **Booking**: One event can have many bookings. Each booking is for one event.

**User** ↔ **Seat**: One user can hold many seats temporarily. Each held seat references the user who holds it (nullable, cleared when hold expires).

**Booking** ↔ **Seat**: One booking can include many seats. Each seat can belong to one booking (nullable until booking is confirmed).

**Booking** ↔ **Payment**:  One booking has one payment. Each payment belongs to one booking.

All entities include audit timestamps (created at, updated at) managed by Hibernate annotations.  The Seat entity includes a version field for optimistic locking.

## Concurrency Management

### The Double-Booking Problem

When two users attempt to reserve the same seat simultaneously, without proper locking, both transactions could read the seat as available, update it to held, and both succeed, resulting in a double-booking scenario.

### Solution Strategy

**During Seat Selection**: Users browsing available seats perform unlocked reads for performance.  Seat availability is displayed based on current state, but not guaranteed until the hold operation.

**During Seat Holding**: When a user clicks to hold seats, the system acquires pessimistic write locks on those specific seat rows. The first user's transaction obtains the locks immediately and proceeds.  The second user's transaction blocks, waiting for the locks.  Once the first transaction commits, the second transaction acquires the locks but now sees the seats as already held, returning an appropriate error message.

**During Booking Confirmation**: Since seats are already held by the specific user at this point, optimistic locking with version fields is sufficient. If another process modified the seat (such as the expiry scheduler), the version mismatch triggers an exception.

**During Expiry Processing**: The scheduled job uses unlocked reads with version checking.  If another transaction modified a seat before the scheduler could release it, the version conflict is detected and safely ignored.

This multi-layered approach balances performance with correctness, applying the strongest guarantees only where conflicts are most likely.

## Scheduled Tasks

QuickTix runs several background jobs to maintain system integrity:

### Release Expired Seat Holds
**Frequency**: Every 5 minutes

Finds all seats in HELD status where the hold timestamp exceeds 15 minutes and releases them back to AVAILABLE.  This ensures inventory doesn't remain locked if users abandon the booking process.

### Expire Pending Bookings
**Frequency**:  Every 5 minutes

Identifies bookings that have remained in PENDING status for more than 15 minutes and marks them as EXPIRED, releasing associated seats.  This handles cases where users create bookings but never complete payment.

### Update Event Statuses
**Frequency**: Every hour

Compares current time against event start and end times, transitioning events from UPCOMING to ONGOING when they start, and from ONGOING to COMPLETED when they end. This keeps event states accurate without manual intervention.

### Retry Failed Refunds
**Frequency**: Every 10 minutes

Safety net for disaster recovery.  Scans all cancelled events and checks for any completed payments that haven't been refunded yet. If the application crashed during an event cancellation before all refunds completed, this job picks up the remaining work and retries.

All schedulers are configured in `SchedulerService.java` with detailed documentation on their purpose and behavior.

## Event-Driven Architecture

When an event is cancelled, the system must refund potentially hundreds or thousands of payments. Processing these synchronously would block the cancellation request for an unacceptable duration.

Instead, QuickTix uses Spring's application event system:

1. `EventService.cancelEventById()` updates the event status and publishes an `EventCancelledEvent`
2. `EventCancellationListener` receives the event asynchronously (marked with `@Async`)
3. The listener queries all completed payments for that event
4. Each payment is refunded in an independent transaction using `Propagation.REQUIRES_NEW`
5. If one refund fails, others continue processing
6. The listener also expires all pending bookings for the cancelled event

This architecture decouples the cancellation request from the refund processing, provides fault tolerance, and improves user experience by not blocking the API response.

Review `EventCancelledEvent. java`, `EventCancellationListener.java`, and the async configuration in `AsyncConfig.java`.

## Getting Started

### Prerequisites
- Java 21 or higher
- Maven 3.8+
- PostgreSQL (for production) or H2 (for development)

### Running Locally

1. Clone the repository:
```bash
git clone https://github.com/kxng0109/QuickTix.git
cd QuickTix

```

2. Configure your database settings in `application-dev.properties` (create this file in `src/main/resources` if it doesn't exist)

3. Build the project:
```bash
mvn clean install
```

4. Run the application:
```bash
mvn spring-boot:run
```

The application will start on port 8080 by default. H2 console (if using H2) will be available at `/h2-console`.

## Configuration

### Profiles

The application supports multiple Spring profiles:

**dev**: Uses H2 in-memory database, enables SQL logging, and recreates schema on each startup.  Suitable for development and testing.

**prod**:  Configured for PostgreSQL with optimized settings for production workloads (to be fully configured before deployment).

Activate a profile by setting `spring.profiles.active` in `application.properties` or via environment variable.

### Key Configuration Properties

**Database**:  Connection URL, username, password, and driver class

**JPA/Hibernate**: DDL auto mode (create-drop for dev, validate for prod), SQL logging, dialect selection

**Scheduling**: Enabled via `@EnableScheduling` annotation in `SchedulerConfiguration. java`

**Async Processing**: Enabled via `@EnableAsync` annotation in `AsyncConfig.java`

All configuration classes are located in the `config` package.

## Technologies Used

- **Spring Boot 4.0.1**: Core framework
- **Spring Data JPA**: Database access and ORM
- **Spring Scheduling**: Background job execution
- **Spring Events**:  Asynchronous event processing
- **Hibernate**: JPA implementation with locking support
- **H2 Database**: In-memory database for development
- **PostgreSQL**:  Production database (configuration ready)
- **Lombok**: Reduces boilerplate code
- **JUnit 5 & Mockito**: Unit and integration testing
- **Maven**:  Dependency management and build tool

## Upcoming Features

- [ ] REST API controllers for all operations
- [ ] Input validation on request DTOs
- [ ] API documentation with Swagger/OpenAPI
- [ ] Email notification service for booking confirmations and event reminders
- [ ] Advanced seat map management (multiple rows, sections, pricing tiers)
- [ ] Bulk seat selection with optimized locking
- [ ] User authentication and authorization with Spring Security
- [ ] Rate limiting to prevent abuse
- [ ] Comprehensive integration tests
- [ ] Metrics and monitoring with Actuator
- [ ] Dockerization for easy deployment
- [ ] CI/CD pipeline configuration
- [ ] Frontend application (React/Vue) to interact with the API

## License

This project is currently unlicensed and intended for educational and portfolio purposes.

## Author

Joshua Ike ([@kxng0109](https://github.com/kxng0109))

---

**Note**: This is an active learning project demonstrating real-world backend development patterns. The codebase emphasizes clean architecture, proper transaction management, and handling complex concurrency scenarios.  Contributions, feedback, and questions are welcome through GitHub issues and discussions.
```