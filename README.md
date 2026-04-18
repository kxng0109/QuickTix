# QuickTix

QuickTix is a production grade ticket booking backend built with Spring Boot. It powers everything an event platform needs, including user accounts, venue and event management, seat reservation with strict concurrency control, booking orchestration, payment processing through real gateways, administrative overrides, asynchronous notifications, and automated background maintenance. The system is designed to behave correctly under heavy traffic, to recover gracefully from partial failures, and to remain understandable to new contributors.

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
21. [Load Testing](#21-load-testing)
22. [Getting Started](#22-getting-started)
23. [Technology Stack](#23-technology-stack)
24. [Project Layout](#24-project-layout)
25. [Roadmap](#25-roadmap)
26. [License](#26-license)
27. [Author](#27-author)

## 1. Overview

Imagine a very busy ticket office. Thousands of people want the same seats at the same time, they pay using different methods, and sometimes the event itself gets cancelled and everyone needs a refund. QuickTix is the invisible machine behind that ticket office. It keeps two people from buying the same seat, it makes sure money and tickets stay in sync, it remembers everything that happened, and it quietly cleans up after itself when users walk away without finishing their purchase.

The backend is written in Java using Spring Boot. It stores its long term data in PostgreSQL, uses Redis for distributed locking, caching, and rate limiting, uses RabbitMQ to hand off email work to a separate notification service, and integrates with Stripe and Paystack for real payment processing.

## 2. Who This Project Is For

QuickTix is intended as a realistic, end to end reference for building transactional backends. It is useful for developers who want to see how authentication, authorization, concurrency, payments, background jobs, caching, rate limiting, and asynchronous messaging fit together in a single coherent codebase. It is also approachable enough that a curious beginner can read one layer at a time and still understand the flow.

## 3. Core Capabilities

QuickTix supports the full booking journey from browsing to refund.

User accounts can be registered, authenticated, updated, and deactivated. Venues can be created and managed by administrators. Events can be scheduled at those venues, and seats are generated automatically when an event is created. End users can browse available seats, hold a group of seats temporarily, create a pending booking for those seats, initialize a payment, and have that booking confirmed automatically once the payment gateway reports success. Administrators can cancel events, which triggers a mass refund workflow, and they can force release seats, force delete users in a privacy respecting way, and view aggregated platform metrics on a dashboard endpoint.

The public surface of the system is exposed through REST controllers. Each controller in `src/main/java/io/github/kxng0109/quicktix/controller/` documents its own endpoints with OpenAPI annotations.

## 4. How the System Is Organized

QuickTix follows a classic layered architecture that is easy to reason about.

The controller layer accepts HTTP requests, validates input, and delegates to services. The service layer holds all business rules and transactional boundaries. The repository layer talks to PostgreSQL through Spring Data JPA. DTOs separate the public API shape from internal entities, and a global exception handler ensures every error response follows the same structure.

Cross cutting behavior is implemented through dedicated building blocks. Servlet filters handle rate limiting and JWT authentication. A scheduler runs periodic maintenance. An application event listener handles mass refunds asynchronously. Configuration classes wire up Redis, RabbitMQ, Stripe, OpenAPI, caching, and security.

You can explore each layer in `src/main/java/io/github/kxng0109/quicktix/`.

## 5. Domain Model

The persistent model is made up of six core entities.

A `User` owns many `Booking` records. A `Venue` hosts many `Event` records. Each `Event` owns a fixed set of `Seat` records, generated at creation time and cascade deleted with the event. A `Booking` belongs to one `User` and one `Event`, contains one or more `Seat` records, and has exactly one associated `Payment`. A `Seat` may also carry a temporary reference to the `User` currently holding it, which is cleared when the hold expires or is released.

Every entity tracks audit timestamps managed by Hibernate. The `Seat` and `Booking` entities include a version field used for optimistic locking. The entity definitions live in `src/main/java/io/github/kxng0109/quicktix/entity/`, and the corresponding status enumerations live in `src/main/java/io/github/kxng0109/quicktix/enums/`.

## 6. Security and Access Control

QuickTix uses stateless JWT authentication backed by Spring Security.

When a user registers or logs in through `AuthController`, the `AuthService` validates credentials and asks `JwtService` to issue a signed token. On every subsequent request, `JwtAuthenticationFilter` extracts the token, verifies its signature and expiration, checks a Redis blacklist of logged out tokens, and populates the Spring Security context. Logout places the token on that blacklist until it would have expired naturally.

Authorization is enforced in two places. Route level rules live in `SecurityConfig`, where public endpoints, user endpoints, and admin endpoints are clearly separated. Resource level ownership is enforced inside services using `AssertOwnershipOrAdmin`, which guarantees that a standard user can only read or modify their own records while administrators retain global access. Unauthorized and forbidden responses are rendered consistently by `JwtAuthenticationEntryPoint` and `JwtAccessDeniedHandler`.

The relevant files are `src/main/java/io/github/kxng0109/quicktix/config/SecurityConfig.java`, `src/main/java/io/github/kxng0109/quicktix/filter/JwtAuthenticationFilter.java`, `src/main/java/io/github/kxng0109/quicktix/service/JwtService.java`, `src/main/java/io/github/kxng0109/quicktix/service/AuthService.java`, and `src/main/java/io/github/kxng0109/quicktix/utils/AssertOwnershipOrAdmin.java`.

## 7. Rate Limiting

To protect the system from abuse and to keep the experience fair during traffic spikes, QuickTix applies a three layered rate limiting strategy built on Bucket4j and Redis.

The first layer throttles traffic by IP address, providing broad protection against scraping and denial of service. The second layer throttles authenticated users by email, capping the velocity of general API calls. The third layer is a strict per user limit on the seat hold endpoint specifically, since seat holding is the most contested operation in the system. All three limits are stored in Redis so they remain consistent across multiple application instances.

The bucket configurations are defined in `src/main/java/io/github/kxng0109/quicktix/config/RateLimitConfig.java`. The filters that enforce them live in `src/main/java/io/github/kxng0109/quicktix/filter/`, and the filter chain ordering is defined in `SecurityConfig` and `FilterConfig`.

## 8. Caching Strategy

Read heavy, rarely changing data is cached in Redis to reduce database pressure.

Venue lookups, event lookups, and available seat queries are annotated with `@Cacheable`. Write operations in the same services use `@CacheEvict` to keep the cache consistent. Caching is globally disabled under the test profile to avoid flaky behavior, which is handled in `RedisConfig` using a profile guard.

The cache annotations can be seen in `EventService`, `VenueService`, and `SeatService` inside `src/main/java/io/github/kxng0109/quicktix/service/`.

## 9. Seat Reservation and Concurrency

Seat reservation is the hardest problem in a ticket booking system because many users compete for the same inventory at the same moment. QuickTix solves this with a layered strategy that balances correctness with performance.

When a user requests to hold a group of seats, the service first sorts the seat identifiers. This deterministic ordering prevents classic deadlock patterns where two transactions try to lock the same resources in opposite order. For each seat, the service then attempts to acquire a distributed lock in Redis through `SeatLockService`, which uses an atomic SETNX operation with a time to live. Redis acts as the fast first line of defense so that most conflicting requests are rejected before they ever touch the database. Only after all Redis locks are acquired does the service move to PostgreSQL, where it loads the same seats under a pessimistic write lock, validates their state, updates them to `HELD`, and records the owning user and the timestamp. If any step fails, all Redis locks acquired so far are released to avoid orphaned state.

Later, when the user confirms a booking, the seats are already known to be held by that user, so optimistic locking via the version column on `Seat` is sufficient to catch the rare case where a scheduler or another flow interferes. The scheduled expiry job also relies on optimistic locking, safely ignoring conflicts when another transaction has already moved the seat forward.

The core files are `src/main/java/io/github/kxng0109/quicktix/service/SeatService.java`, `src/main/java/io/github/kxng0109/quicktix/service/SeatLockService.java`, and `src/main/java/io/github/kxng0109/quicktix/repositories/SeatRepository.java`.

## 10. Booking Lifecycle

A booking passes through a small and well defined set of states.

It begins in `PENDING` when the user turns their held seats into a booking. It becomes `CONFIRMED` after the payment gateway reports success and the system upgrades the seats to `BOOKED`. It becomes `CANCELLED` if the user cancels a pending booking or if an administrator or refund flow cancels it. It becomes `EXPIRED` if the user never completes payment within the allowed window, at which point the scheduler releases the seats back to the public pool.

The orchestration logic is in `src/main/java/io/github/kxng0109/quicktix/service/BookingService.java`, and the public reference codes used for customer support lookups are generated by `src/main/java/io/github/kxng0109/quicktix/utils/BookingReferenceGenerator.java` using a cryptographically strong random source that avoids visually confusing characters.

## 11. Payments and Refunds

QuickTix talks to real payment providers through a clean abstraction.

The `PaymentGateway` interface defines two operations, initializing a payment session and refunding a completed transaction. Two production implementations exist, one for Stripe and one for Paystack, selected at runtime through a Spring profile and the `payment.gateway.provider` property. A mock implementation is used in the test and mock profiles so that tests never reach the internet. Both real gateways embed the internal payment identifier in provider metadata so that the correct internal record can be located when a webhook arrives later.

Payment initialization is protected by idempotency. Clients must send an `Idempotency-Key` header, which is combined with a short lived Redis lock so that retries caused by flaky mobile networks never create duplicate charges. If the same key is seen twice, the previously created payment response is returned. Refunds are gated by strict status checks, and refunds triggered by event cancellations run inside their own `REQUIRES_NEW` transactions so that one failed refund never rolls back the others.

The relevant files are `src/main/java/io/github/kxng0109/quicktix/service/PaymentService.java` and the gateway implementations inside `src/main/java/io/github/kxng0109/quicktix/service/gateway/`.

## 12. Webhooks

Payment success is considered final only when the provider says so, not when the browser says so.

Two dedicated webhook controllers listen for provider notifications. The Stripe controller verifies the `Stripe-Signature` header using the official SDK. The Paystack controller computes an HMAC SHA 512 of the raw payload and compares it with the `x-paystack-signature` header. Both endpoints are whitelisted in the security configuration so they can be reached without a JWT, yet they remain safe because unsigned or mis signed requests are rejected before any business logic runs. Once verified, the controller extracts the internal payment identifier from the provider metadata and asks `PaymentService.handleSuccessfulWebhookPayment` to mark the payment as completed and confirm the booking. The operation is idempotent, so duplicate webhook deliveries are harmless.

The webhook controllers are in `src/main/java/io/github/kxng0109/quicktix/controller/webhook/`.

## 13. Event Driven Processing

Cancelling a popular event can mean issuing hundreds or thousands of refunds. Doing that inside an HTTP request would be unacceptable.

Instead, `EventService.cancelEventById` marks the event as cancelled and publishes an `EventCancelledEvent` application event. `EventCancellationListener` receives that event asynchronously, fetches every completed payment for the cancelled event, and refunds each one in its own independent transaction. It also expires any lingering pending bookings for the same event. Because each refund runs in isolation, a single gateway failure never blocks or rolls back the others.

The files involved are `src/main/java/io/github/kxng0109/quicktix/event/EventCancelledEvent.java`, `src/main/java/io/github/kxng0109/quicktix/listener/EventCancellationListener.java`, and `src/main/java/io/github/kxng0109/quicktix/config/AsyncConfig.java`.

## 14. Scheduled Maintenance Jobs

A small set of background jobs keeps the system tidy without any human intervention.

One job releases seat holds whose timers have expired, so that abandoned checkouts never lock inventory indefinitely. Another job expires pending bookings that never received a payment, freeing their seats. A third job advances event statuses through `UPCOMING`, `ONGOING`, and `COMPLETED` based on the current time. A fourth job acts as a safety net for refunds, scanning cancelled events for any payments that are still marked completed and retrying them, which protects the system against mid cancellation crashes. A fifth job dispatches reminder emails roughly twenty four hours before an event begins.

All of this is orchestrated in `src/main/java/io/github/kxng0109/quicktix/service/SchedulerService.java`, and scheduling itself is enabled in `src/main/java/io/github/kxng0109/quicktix/config/SchedulerConfiguration.java` outside of the test profile.

## 15. Notifications

Sending email from inside a request thread would make the API slow and fragile, so QuickTix offloads notifications to a message broker.

`NotificationPublisherService` serializes a `NotificationRequest` into JSON and publishes it to a RabbitMQ exchange. A separate service, NotifyHub, consumes those messages and takes care of actually delivering the emails. This keeps QuickTix fast, keeps retry logic out of the booking path, and makes it easy to change delivery providers without touching the booking code.

The producer lives in `src/main/java/io/github/kxng0109/quicktix/service/NotificationPublisherService.java`, the broker wiring is in `src/main/java/io/github/kxng0109/quicktix/config/RabbitMQConfig.java`, and the DTO contract is defined in `src/main/java/io/github/kxng0109/quicktix/dto/request/message/`.

## 16. API Documentation

Every public endpoint is documented with OpenAPI 3 annotations, including example payloads and error shapes. When the application is running, the interactive Swagger UI is available at `/swagger-ui.html` and the raw specification at `/api-docs`. Administrative and webhook endpoints are intentionally hidden from the documentation.

The global OpenAPI metadata is configured in `src/main/java/io/github/kxng0109/quicktix/config/OpenApiConfig.java`, and per endpoint annotations are applied directly on the controller methods inside `src/main/java/io/github/kxng0109/quicktix/controller/`.

## 17. Error Handling

Every error coming out of the API follows a single predictable shape. `GlobalExceptionHandler` translates validation failures, entity lookups, authorization failures, domain exceptions, optimistic locking conflicts, and gateway errors into structured responses with a timestamp, status code, reason phrase, message, and request path. This makes client side handling straightforward and keeps logs easy to scan.

The handler is in `src/main/java/io/github/kxng0109/quicktix/exception/GlobalExceptionHandler.java`, and the response record is in `src/main/java/io/github/kxng0109/quicktix/dto/exception/ErrorResponse.java`. Custom domain exceptions are grouped in `src/main/java/io/github/kxng0109/quicktix/exception/`.

## 18. Testing Strategy

QuickTix is covered at three levels.

Unit tests exercise each service in isolation with Mockito, verifying branches, transitions, and edge cases. Controller tests use `@WebMvcTest` with MockMvc to validate routing, validation, serialization, authorization, and error mapping. Integration tests load the full Spring context against an in memory H2 database and drive real HTTP traffic through the stack, covering the complete booking flow, security rules, and venue management.

The tests live in `src/test/java/io/github/kxng0109/quicktix/` under `controller`, `service`, and `integration` subpackages. Configuration specific to tests is kept in `src/test/resources/application-test.properties`.

## 19. Configuration and Profiles

QuickTix ships with three Spring profiles.

The `dev` profile is the default local setup and targets a local PostgreSQL database alongside a local RabbitMQ and Redis instance. The `mock` profile is useful for load testing and swaps the payment gateway for the in memory mock implementation while keeping a real database. The `test` profile targets an H2 in memory database and disables scheduling and caching to keep tests deterministic.

Sensitive values such as the JWT secret, Stripe keys, and Paystack keys are injected through environment variables. The property files are `src/main/resources/application.properties`, `src/main/resources/application-dev.properties`, `src/main/resources/application-mock.properties`, and `src/test/resources/application-test.properties`.

## 20. Infrastructure and Deployment

A `docker-compose.yml` file at the repository root brings up the supporting services that QuickTix depends on, including PostgreSQL, Redis, and RabbitMQ. An `nginx.conf` file is provided to front multiple application instances behind a load balancer, which is also reflected in the OpenAPI server list. A prebuilt IntelliJ run configuration named Docker down up is included under `.run/` to make starting and stopping the local environment a single click operation, and a second run configuration named Run servers helps launch the application itself.

## 21. Load Testing

Three k6 scripts of increasing intensity are provided at the repository root, named `load-test-1k.js`, `load-test-4k.js`, and `load-test-10k.js`. They are useful for verifying that rate limiting, seat locking, and the payment idempotency flow behave correctly under realistic and extreme traffic. A prepared `tokens.json` file is available to seed authenticated virtual users, and `e2e-test.http` contains ready to run requests for manual exploration.

## 22. Getting Started

The minimum requirements are a recent Java Development Kit, Maven, Docker, and Docker Compose. PostgreSQL, Redis, and RabbitMQ can all be started through the provided Docker Compose file, or you can point the application at existing instances by editing the property files.

After cloning the repository, supply the required environment variables for the JWT secret and the payment provider keys, start the supporting services, and launch the application through Maven or your IDE. Once the application is running, the Swagger UI provides the fastest way to explore every endpoint. If you prefer a scripted tour, the `e2e-test.http` file walks through a full booking journey step by step.

## 23. Technology Stack

QuickTix is built on Spring Boot 4 with Spring Security, Spring Data JPA, Spring Cache, Spring AMQP, and Spring Scheduling. Persistence is provided by Hibernate against PostgreSQL in production and H2 in tests. Redis, accessed through Lettuce, powers distributed locking, caching, rate limiting, and the JWT blacklist. RabbitMQ carries asynchronous notification jobs to the NotifyHub service. Payment integration uses the official Stripe Java SDK and a hand built Paystack client. Bucket4j implements the token bucket algorithm for rate limiting. Lombok removes boilerplate, JJWT handles token signing, springdoc generates OpenAPI documentation, and JUnit 5, Mockito, and Spring Test drive the test suite.

## 24. Project Layout

At a high level, the source tree under `src/main/java/io/github/kxng0109/quicktix/` is organized into `config` for infrastructure wiring, `controller` for HTTP endpoints, `dto` for request and response contracts, `entity` for persistent models, `enums` for status values, `event` and `listener` for the asynchronous cancellation flow, `exception` for domain errors and the global handler, `filter` for authentication and rate limiting, `repositories` for Spring Data interfaces, `security` for authentication entry points and access denied handlers, `service` for business logic including the `gateway` subpackage for payment providers, and `utils` for small helpers. Tests follow the same package structure under `src/test/java/`.

## 25. Roadmap

Planned future work includes richer seat mapping with rows, sections, and pricing tiers, a first party frontend that consumes the existing API, broader observability through Spring Boot Actuator and metrics exporters, expanded integration test coverage for the payment webhooks, and a full continuous integration and deployment pipeline.

## 26. License

This project is currently provided without a license and is intended for educational and portfolio purposes. If you plan to reuse it in a commercial setting, please open an issue first so that a suitable license can be added.

## 27. Author

QuickTix is written and maintained by Joshua Ike. You can find more of the author's work at [github.com/kxng0109](https://github.com/kxng0109). Feedback, questions, and contributions are welcome through GitHub issues and pull requests.
```