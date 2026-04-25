package io.github.kxng0109.quicktix.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Global configuration enabling Spring's scheduled task execution.
 * <p>
 * Activates the {@code @Scheduled} annotations across the application (e.g., in {@code SchedulerService}).
 * It is disabled in the "test" profile to prevent background jobs from firing unpredictably
 * during unit and integration testing, which could lead to race conditions or flaky tests.
 * </p>
 */
@Configuration
@EnableScheduling
@Profile({"!test", "!slice-test"})
public class SchedulerConfiguration {

}
