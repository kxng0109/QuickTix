package io.github.kxng0109.quicktix.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;

public record EventDateSearchRequest(
        @NotNull(message = "Start date can't be null")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant startDate,

        @NotNull(message = "End date can't be null")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant endDate
) {
    @AssertTrue(message = "Start date must be before end date")
    public boolean isDateRangeValid() {
        if(startDate == null || endDate == null) return true; //We want the @NotNull annotation to handle it
        return startDate.isBefore(endDate);
    }
}
