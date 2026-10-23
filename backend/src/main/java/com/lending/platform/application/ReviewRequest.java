package com.lending.platform.application;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ReviewRequest(
        @NotEmpty List<@NotNull AgentType> requestedChecks,
        @Size(max = 1000) String reviewNote) {
}
