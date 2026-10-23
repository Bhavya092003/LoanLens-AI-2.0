package com.lending.platform.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateReviewerRequest(
        @NotBlank @Size(min = 3, max = 120) String username,
        @NotBlank @Size(min = 8, max = 128) String password,
        @NotBlank @Size(max = 200) String fullName) {
}
