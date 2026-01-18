package io.github.kxng0109.quicktix.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SeatStatus {
    AVAILABLE("Available"),
    HELD("Held"),
    BOOKED("Booked");

    private final String displayName;

    @JsonValue
    public String getDisplayName() {
        return displayName;
    }
}