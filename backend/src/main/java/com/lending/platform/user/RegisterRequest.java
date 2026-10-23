package com.lending.platform.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 120) String username,
        @NotBlank @Size(min = 8, max = 128) String password,
        @NotBlank @Size(max = 200) String fullName,
        @Email @Size(max = 320) String email) {
}
