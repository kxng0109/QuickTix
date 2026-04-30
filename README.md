# QuickTix

QuickTix is a production grade ticket booking backend built with Spring Boot. It powers everything an event platform
needs, including user accounts, venue and event management, seat reservation with high-concurrency protection, and
secure payment processing.

## Table of Contents

1. [Overview](#1-overview)
2. [Who This Project Is For](#2-who-this-project-is-for)
3. [Core Capabilities](#3-core-capabilities)
4. [How the System Is Organized](#4-how-the-system-is-organized)
5. [Domain Model](#5-domain-model)
6. [Security and Access Control](#6-security-and-access-control)
7. [Rate Limiting](#7-rate-limiting)
8. [Caching Strategy](#8-caching-strategy)
9. [Seat Reservation and Concurrency](#9-seat-reservation-and-concurrency)
10. [Booking Lifecycle](#10-booking-lifecycle)
11. [Payments and Refunds](#11-payments-and-refunds)
12. [Webhooks](#12-webhooks)
13. [Event Driven Processing](#13-event-driven-processing)
14. [Scheduled Maintenance Jobs](#14-scheduled-maintenance-jobs)
15. [Notifications](#15-notifications)
16. [API Documentation](#16-api-documentation)
17. [Error Handling](#17-error-handling)
18. [Testing Strategy](#18-testing-strategy)
19. [Configuration and Profiles](#19-configuration-and-profiles)
20. [Infrastructure and Deployment](#20-infrastructure-and-deployment)
21. [Load Testing & Performance](#21-load-testing--performance)
22. [Getting Started](#22-getting-started)
23. [Technology Stack](#23-technology-stack)
24. [Project Layout](#24-project-layout)
25. [Roadmap](#25-roadmap)
26. [License](#26-license)
27. [Author](#27-author)

## 1. Overview

Imagine a very busy ticket office. Thousands of people want the same seats at the same time, they pay using different
methods, and sometimes the event itself gets cancelled and everyone needs a refund. QuickTix handles all of this
automatically, safely, and efficiently.

The backend is written in Java using Spring Boot. It stores its long term data in PostgreSQL, uses Redis for distributed
locking, caching, and rate limiting, uses RabbitMQ to hand off email work to external workers, and integrates with real
payment gateways like Stripe and Paystack.

## 2. Who This Project Is For

QuickTix is intended as a realistic, end-to-end reference for building transactional backends. It is useful for
developers who want to see how authentication, authorization, concurrency, payments, and background jobs fit together in
a cohesive, production-ready system.

## 3. Core Capabilities

QuickTix supports the full booking journey from browsing to refund.

User accounts can be registered, authenticated, updated, and deactivated. Venues can be created and managed by
administrators. Events can be scheduled at those venues, and highly structured spatial seating charts (Sections, Rows,
and Seats) are generated automatically based on the requested layout using optimized JDBC batch inserts. Event metadata
can be partially modified later via strict `PATCH` endpoints, while physical venue layouts remain immutably locked to
prevent data corruption.

The public surface of the system is exposed through REST controllers. Each controller in
`src/main/java/io/github/kxng0109/quicktix/controller/` documents its own endpoints with OpenAPI annotations.

## 4. How the System Is Organized

QuickTix follows a classic layered architecture that is easy to reason about.

The controller layer accepts HTTP requests, validates input, and delegates to services. The service layer holds all
business rules and transactional boundaries. The repository layer talks to PostgreSQL using Spring Data JPA.

Cross cutting behavior is implemented through dedicated building blocks. Servlet filters handle rate limiting and JWT
authentication. A scheduler runs periodic maintenance. An application event listener manages async workflows like mass
refunds.

You can explore each layer in `src/main/java/io/github/kxng0109/quicktix/`.

## 5. Domain Model

The persistent model is made up of eight core entities.

A `User` owns many `Booking` records. A `Venue` hosts many `Event` records. QuickTix uses a hierarchical spatial seating
model: an `Event` owns `Section` records, which own `Row` records, which own `Seat` records. This inventory is
deterministically generated at creation time using level-by-level JDBC batch inserts to maximize performance and prevent
memory exhaustion. Ticket prices are stamped directly onto individual seats to support dynamic pricing, premium aisles,
and obstructed views. A `Booking` tracks a financial transaction, aggregating multiple reserved `Seat` records and
linking to a `Payment`.

Every entity tracks audit timestamps managed by Hibernate. The `Seat` and `Booking` entities include a version field
used for optimistic locking. The entity definitions live in `src/main/java/io/github/kxng0109/quicktix/entity/`.

## 6. Security and Access Control

QuickTix uses stateless JWT authentication backed by Spring Security.

When a user registers or logs in through `AuthController`, the `AuthService` validates credentials and asks `JwtService`
to issue a signed token. On every subsequent request, `JwtAuthenticationFilter` intercepts the request, verifies the
signature, and reconstructs the security principal.

Authorization is enforced in two places. Route level rules live in `SecurityConfig`, where public endpoints, user
endpoints, and admin endpoints are clearly separated. Resource level ownership is asserted inside the services, ensuring
that a user can only view or modify their own bookings.

The relevant files are `src/main/java/io/github/kxng0109/quicktix/config/SecurityConfig.java`,
`src/main/java/io/github/kxng0109/quicktix/filter/JwtAuthenticationFilter.java`,
`src/main/java/io/github/kxng0109/quicktix/service/AuthService.java`, and
`src/main/java/io/github/kxng0109/quicktix/utils/AssertOwnershipOrAdmin.java`.

## 7. Rate Limiting

To protect the system from abuse and to keep the experience fair during traffic spikes, QuickTix applies a three layered
rate limiting strategy built on Bucket4j and Redis.

The first layer throttles traffic by IP address, providing broad protection against scraping and denial of service. The
second layer throttles authenticated users by email, capping the velocity of individual accounts. The third layer
provides a much stricter budget specifically for sensitive authentication endpoints to slow down brute force attacks.

The bucket configurations are defined in `src/main/java/io/github/kxng0109/quicktix/config/RateLimitConfig.java`. The
filters that enforce them live in `src/main/java/io/github/kxng0109/quicktix/filter/`.

## 8. Caching Strategy

Read heavy, rarely changing data is cached in Redis to reduce database pressure.

Venue lookups, event lookups, and available seat queries are annotated with `@Cacheable`. Write operations in the same
services use `@CacheEvict` to keep the cache consistent. Caching is globally enabled and uses a specialized Redis cache
manager that handles JSON serialization automatically.

The cache annotations can be seen in `EventService`, `VenueService`, and `SeatService` inside
`src/main/java/io/github/kxng0109/quicktix/service/`.

## 9. Seat Reservation and Concurrency

Seat reservation is the hardest problem in a ticket booking system because many users compete for the same inventory at
the same moment. QuickTix solves this with a layered strategy that balances correctness with performance.

When a user requests to hold a group of seats, the service first sorts the seat identifiers. This deterministic ordering
prevents classic deadlock patterns where two transactions try to lock the same resources in opposite orders. The system
utilizes Redis-backed pessimistic locking to secure the seats; under distributed load testing, this mechanism
successfully blocked thousands of concurrent collisions without a single double-booking or database deadlock.

Later, when the user confirms a booking, the seats are already known to be held by that user, so optimistic locking via
the version column on `Seat` is sufficient to catch the rare case where a scheduler or another flow interferes. The
scheduled expiry job also relies on optimistic locking, safely ignoring conflicts when another transaction has already
moved the seat forward.

The core files are `src/main/java/io/github/kxng0109/quicktix/service/SeatService.java`,
`src/main/java/io/github/kxng0109/quicktix/service/SeatLockService.java`, and
`src/main/java/io/github/kxng0109/quicktix/repositories/SeatRepository.java`.

## 10. Booking Lifecycle

A booking passes through a small and well defined set of states.

It begins in `PENDING` when the user turns their held seats into a booking. It becomes `CONFIRMED` after the payment
gateway reports success and the system upgrades the seats to `BOOKED`. It becomes `CANCELLED` if the user cancels a
pending booking or if an administrator or refund flow cancels it. It becomes `EXPIRED` if the user never completes
payment within the allowed window, at which point the scheduler releases the seats back to the public pool.

The orchestration logic is in `src/main/java/io/github/kxng0109/quicktix/service/BookingService.java`, and the public
reference codes used for customer support lookups are generated by
`src/main/java/io/github/kxng0109/quicktix/utils/BookingReferenceGenerator.java` using a cryptographically strong random
source that avoids visually confusing characters.

## 11. Payments and Refunds

QuickTix talks to real payment providers through a clean abstraction.

The `PaymentGateway` interface defines two operations, initializing a payment session and refunding a completed
transaction. Two production implementations exist, one for Stripe and one for Paystack, selected at runtime through a
Spring profile and the `payment.gateway.provider` property. A mock implementation is used in the test and mock profiles
so that tests never reach the internet. Both real gateways embed the internal payment identifier in provider metadata so
that the correct internal record can be located when a webhook arrives later.

Payment initialization is protected by idempotency. Clients must send an `Idempotency-Key` header, which is combined
with a short lived Redis lock so that retries caused by flaky mobile networks never create duplicate charges. If the
same key is seen twice, the previously created payment response is returned. Refunds are gated by strict status checks,
and refunds triggered by event cancellations run inside their own `REQUIRES_NEW` transactions so that one failed refund
never rolls back the others.

The relevant files are `src/main/java/io/github/kxng0109/quicktix/service/PaymentService.java` and the gateway
implementations inside `src/main/java/io/github/kxng0109/quicktix/service/gateway/`.

## 12. Webhooks

Payment success is considered final only when the provider says so, not when the browser says so.

Two dedicated webhook controllers listen for provider notifications. The Stripe controller verifies the
`Stripe-Signature` header using the official SDK. The Paystack controller computes an HMAC SHA512 hash of the payload
using its secret key and compares it to the `x-paystack-signature` header in constant time to prevent timing attacks.
Both controllers translate the payload into domain terms and pass it to the service layer for final processing.

The webhook controllers are in `src/main/java/io/github/kxng0109/quicktix/controller/webhook/`.

## 13. Event Driven Processing

Cancelling a popular event can mean issuing hundreds or thousands of refunds. Doing that inside an HTTP request would be
unacceptable.

Instead, `EventService.cancelEventById` marks the event as cancelled and publishes an `EventCancelledEvent` application
event. `EventCancellationListener` receives that event asynchronously, fetches all completed payments, and issues the
refunds in a background thread. This keeps the API incredibly snappy.

The files involved are `src/main/java/io/github/kxng0109/quicktix/event/EventCancelledEvent.java`,
`src/main/java/io/github/kxng0109/quicktix/listener/EventCancellationListener.java`, and
`src/main/java/io/github/kxng0109/quicktix/service/EventService.java`.

## 14. Scheduled Maintenance Jobs

A small set of background jobs keeps the system tidy without any human intervention.

One job releases seat holds whose timers have expired, so that abandoned checkouts never lock inventory indefinitely.
Another job expires pending bookings that never received a payment, freeing the system from stale transactions. Another
updates event statuses based on the clock, automatically moving them to ongoing or completed.

All of this is orchestrated in `src/main/java/io/github/kxng0109/quicktix/service/SchedulerService.java`, and scheduling
itself is enabled in `src/main/java/io/github/kxng0109/quicktix/config/SchedulerConfig.java`.

## 15. Notifications

Sending email from inside a request thread would make the API slow and fragile, so QuickTix offloads notifications to a
message broker.

`NotificationPublisherService` serializes a `NotificationRequest` into JSON and publishes it to a RabbitMQ exchange. A
separate service, NotifyHub, consumes those messages and takes care of actual delivery and templating, allowing the
booking system to remain focused on its core responsibilities.

The producer lives in `src/main/java/io/github/kxng0109/quicktix/service/NotificationPublisherService.java`, the broker
wiring is in `src/main/java/io/github/kxng0109/quicktix/config/RabbitMQConfig.java`.

## 16. API Documentation

Every public endpoint is documented with OpenAPI 3 annotations, including example payloads and error shapes. When the
application is running, the interactive Swagger UI is available at `/swagger-ui.html`, and the raw JSON specification is
at `/v3/api-docs`.

The global OpenAPI metadata is configured in `src/main/java/io/github/kxng0109/quicktix/config/OpenApiConfig.java`, and
per endpoint annotations are applied directly on the controller methods inside
`src/main/java/io/github/kxng0109/quicktix/controller/`.

## 17. Error Handling

Every error coming out of the API follows a single predictable shape. `GlobalExceptionHandler` translates validation
failures, entity lookups, authorization failures, domain exceptions, optimistic locking errors, and unexpected server
errors into a standard `ErrorResponse` structure.

The handler is in `src/main/java/io/github/kxng0109/quicktix/exception/GlobalExceptionHandler.java`, and the response
record is in `src/main/java/io/github/kxng0109/quicktix/dto/exception/ErrorResponse.java`.

## 18. Testing Strategy

QuickTix is covered at three levels.

Unit tests exercise each service in isolation with Mockito, verifying branches, transitions, and edge cases. Controller
tests use `@WebMvcTest` with MockMvc to validate routing, validation, serialization, and authorization without starting
a real server. Integration tests bring up the full Spring context alongside Testcontainers (PostgreSQL and Redis) to
prove that the database queries and caching layers actually work in reality.

The tests live in `src/test/java/io/github/kxng0109/quicktix/` under `controller`, `service`, and `integration`
subpackages. Configuration specific to tests is kept in `src/test/resources/application-test.yml`.

## 19. Configuration and Profiles

QuickTix ships with specific Spring profiles to handle different environments. The `dev` profile is the default local
setup and targets a local PostgreSQL database alongside local RabbitMQ and Redis instances. The `test` profile disables
physical RabbitMQ connections and relies on embedded Testcontainers. The `mock-stress-test` profile runs special data
seeders for k6 load testing scenarios.

## 20. Infrastructure and Deployment

A `docker-compose.yml` file at the repository root brings up the supporting services that QuickTix depends on, including
PostgreSQL, Redis, and RabbitMQ. An `nginx.conf` file is provided to front multiple JVMs during extreme local load
testing, proving out the distributed architecture.

## 21. Load Testing & Performance

QuickTix is built to survive extreme traffic spikes (e.g., ticket drops for major concerts). To prove the architecture's
resilience, the system was load-tested using **k6** in a distributed local environment.

**The Test Architecture:**

* 3 concurrent Spring Boot JVMs running behind an Nginx load balancer.
* 1 PostgreSQL database (HikariCP connection pool).
* 1 Redis cluster (for pessimistic locking & rate limiting).
* 1 RabbitMQ broker (for asynchronous email tasks).
* 300 concurrent Virtual Users (VUs) aggressively attempting to buy from a pool of 50 seats.

**The Results:**

* **Throughput:** Sustained **100+ HTTP requests per second** across the cluster.
* **Concurrency Defense:** Over 4,400 intentional race conditions were generated (multiple users trying to buy the exact
  same seat at the exact same millisecond). The Redis pessimistic locks successfully rejected every single overlapping
  claim, preventing double-bookings.
* **Asynchronous Offloading:** The system successfully processed over 1,500 completed checkouts in 3 minutes, generating
  1,500+ cryptographically signed webhooks and seamlessly publishing 1,500+ async events to RabbitMQ.

The k6 test scripts are available in the repository root (e.g., `load-test-rabbitmq.js`) for reproducible verification.

## 22. Getting Started

The minimum requirements are a recent Java Development Kit, Maven, Docker, and Docker Compose. PostgreSQL, Redis, and
RabbitMQ can all be started through the provided Docker Compose file, or you can supply your own connection strings.

After cloning the repository, supply the required environment variables for the JWT secret and the payment provider
keys, start the supporting services, and launch the application through Maven or your IDE using the `dev` profile.

## 23. Technology Stack

QuickTix is built on Spring Boot 4 with Spring Security, Spring Data JPA, Spring Cache, Spring AMQP, and Spring
Scheduling. Persistence is provided by Hibernate against **PostgreSQL**, with database schema management powered by
Flyway. Advanced caching and rate limiting run on Redis via Redisson and Bucket4j.

## 24. Project Layout

At a high level, the source tree under `src/main/java/io/github/kxng0109/quicktix/` is organized into `config` for
infrastructure wiring, `controller` for HTTP endpoints, `dto` for request and response payloads, `entity` for the
database domain, `exception` for error handling, `repository` for data access, and `service` for the core business
logic.

## 25. Roadmap

Planned future work includes a first-party frontend that consumes the existing API, broader observability through Spring
Boot Actuator with Micrometer/Prometheus, and implementing a circuit breaker for external payment gateway calls to
ensure platform stability during provider outages.

## 26. License

This project is currently provided without a license and is intended for educational and portfolio purposes. If you plan
to reuse it in a commercial setting, please open an issue first so that a proper open source license can be attached.

## 27. Author

QuickTix is written and maintained by Joshua Ike. You can find more of the author's work
at [github.com/kxng0109](https://github.com/kxng0109). Feedback, questions, and contributions are welcome through issues
or direct contact.