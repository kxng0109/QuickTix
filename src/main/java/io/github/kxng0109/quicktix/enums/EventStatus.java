package io.github.kxng0109.quicktix.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EventStatus {

    // Define the constant and how you want it to look to the user
    UPCOMING("Upcoming"),
    ONGOING("Ongoing"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled");

    // This field holds the "Upcoming" text
    private final String displayName;

    @JsonValue // Java UPCOMING -> JSON "Upcoming"
    public String getDisplayName() {
        return displayName;
    }
}
