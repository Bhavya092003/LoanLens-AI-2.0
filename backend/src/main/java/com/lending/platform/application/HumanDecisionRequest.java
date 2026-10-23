package com.lending.platform.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record HumanDecisionRequest(
        @NotBlank String decision,
        @Size(max = 2000) String note,
        @Size(max = 10) List<@Size(max = 64) String> requestedDocumentTypes) {
}
