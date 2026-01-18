package io.github.kxng0109.quicktix.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record CreateUserRequest(
        @NotBlank(message = "First name can't be blank")
        @Size(max = 64, message = "First name can't be more than 64 characters")
        String firstName,

        @NotBlank(message = "Last name can't be blank")
        @Size(max = 64, message = "Last name can't be more than 64 characters")
        String lastName,

        @NotBlank(message = "Email can't be blank")
        @Email(message = "Email must be valid")
        String email,

        @Size(max = 15, message = "Phone number must be at most 15 characters")
        String phoneNumber
) {
}
