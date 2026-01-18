package io.github.kxng0109.quicktix.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BookingStatus {
    PENDING("Pending"),
    CONFIRMED("Confirmed"),
    CANCELLED("Cancelled"),
    EXPIRED("Expired");

    private final String displayName;

    @JsonValue
    public String getDisplayName() {
        return displayName;
    }
}