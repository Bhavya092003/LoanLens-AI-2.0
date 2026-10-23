package com.lending.platform.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ApplicationDocumentRequest(
        @NotBlank @Size(max = 64) String documentType,
        @NotBlank @Size(max = 255) String originalFilename,
        @NotBlank @Size(max = 160) String contentType,
        @NotNull @PositiveOrZero Long fileSizeBytes) {
}
