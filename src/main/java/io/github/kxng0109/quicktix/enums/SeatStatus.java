package io.github.kxng0109.quicktix.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Represents the availability and reservation state of a physical seat at a venue.
 * <p>
 * Works in tandem with Redis distributed locks. A seat cannot transition from {@code AVAILABLE}
 * to {@code BOOKED} without first passing through the {@code HELD} state during checkout.
 * </p>
 */
@Getter
@RequiredArgsConstructor
public enum SeatStatus {
    /**
     * Seat is open to the public and can be selected for checkout.
     */
    AVAILABLE("Available"),

    /**
     * Seat is temporarily locked by a specific user who is currently in the checkout flow.
     * Prevents double-booking race conditions.
     */
    HELD("Held"),

    /**
     * Seat has been fully paid for and is permanently assigned to a user's booking.
     */
    BOOKED("Booked");

    private final String displayName;

    @JsonValue
    public String getDisplayName() {
        return displayName;
    }
}