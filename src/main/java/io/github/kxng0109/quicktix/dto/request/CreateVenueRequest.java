package io.github.kxng0109.quicktix.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateVenueRequest(
        @NotBlank(message = "name can't be blank")
        String name,

        @NotBlank(message = "address can't be blank")
        String address,

        @NotBlank(message = "city can't be blank")
        String city,

        @NotBlank(message = "totalCapacity can not be blank")
        @Min(value = 0, message = "totalCapacity must be greater than 0")
        Integer totalCapacity
) {
}
