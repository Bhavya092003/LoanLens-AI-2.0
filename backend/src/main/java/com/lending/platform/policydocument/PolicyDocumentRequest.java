package com.lending.platform.policydocument;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record PolicyDocumentRequest(
        @NotBlank @Size(max = 240) String documentName,
        @NotBlank @Size(max = 64) String documentType,
        @NotBlank @Size(max = 64) String version,
        LocalDate effectiveDate) {
}
