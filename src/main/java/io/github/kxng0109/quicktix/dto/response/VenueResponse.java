package io.github.kxng0109.quicktix.dto.response;

import lombok.Builder;

@Builder
public record VenueResponse(
        Long id,
        String name,
        String address,
        String city,
        Integer totalCapacity
) {
}
