package io.github.kxng0109.quicktix.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Represents the chronological lifecycle of an event.
 * <p>
 * This status is dynamically managed by the {@link io.github.kxng0109.quicktix.service.SchedulerService}
 * based on the current time relative to the event's start and end times, or manually overridden
 * by administrators via cancellations.
 * </p>
 */
@Getter
@RequiredArgsConstructor
public enum EventStatus {
    /**
     * Event is scheduled for the future. Tickets are available for purchase.
     */
    UPCOMING("Upcoming"),

    /**
     *  Event is currently taking place.
     */
    ONGOING("Ongoing"),

    /**
     *  Event has concluded. No further bookings or refunds can be processed.
     */
    COMPLETED("Completed"),

    /**
     *  Event was aborted by an administrator. Triggers the mass-refund protocol.
     */
    CANCELLED("Cancelled");

    // This field holds the "Upcoming" text
    private final String displayName;

    @JsonValue // Java UPCOMING -> JSON "Upcoming"
    public String getDisplayName() {
        return displayName;
    }
}
