package com.lending.platform.policyqa;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PolicyQuestionRequest(
        @NotBlank @Size(max = 4000) String question,
        @Size(max = 4000) String context,
        @Size(max = 64) String documentType,
        @Size(max = 64) String version) {
}
