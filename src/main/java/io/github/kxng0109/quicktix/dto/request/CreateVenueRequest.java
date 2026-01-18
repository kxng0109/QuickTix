package io.github.kxng0109.quicktix.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record CreateVenueRequest(
        @NotBlank(message = "Venue name can't be blank")
        String name,

        @NotBlank(message = "Address can't be blank")
        String address,

        @NotBlank(message = "City can't be blank")
        String city,

        @NotBlank(message = "Venue total capacity can not be blank")
        @Min(value = 0, message = "Venue total capacity must be greater than 0")
        Integer totalCapacity
) {
}
