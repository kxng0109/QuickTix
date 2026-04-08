package io.github.kxng0109.quicktix.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Represents the lifecycle state of a ticket booking within the QuickTix platform.
 * <p>
 * This state dictates whether seats are temporarily held, permanently assigned,
 * or released back to the public pool.
 * </p>
 */
@Getter
@RequiredArgsConstructor
public enum BookingStatus {
    /**
     * Booking initiated but awaiting successful payment verification.
     * Seats are temporarily locked in Redis.
     */
    PENDING("Pending"),

    /**
     *  Payment successfully processed. Seats are permanently assigned to the user.
     */
    CONFIRMED("Confirmed"),

    /**
     *  Booking was intentionally cancelled (e.g., user requested a refund or event was cancelled).
     */
    CANCELLED("Cancelled"),

    /**
     * Checkout time elapsed before payment was completed. Seats have been released.
     */
    EXPIRED("Expired");

    private final String displayName;

    @JsonValue
    public String getDisplayName() {
        return displayName;
    }
}