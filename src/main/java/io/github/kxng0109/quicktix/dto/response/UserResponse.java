package io.github.kxng0109.quicktix.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public record UserResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String phoneNumber
) {
}
