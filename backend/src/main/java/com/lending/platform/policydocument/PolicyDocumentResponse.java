package com.lending.platform.policydocument;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PolicyDocumentResponse(UUID documentId, String documentName, String documentType,
                                     String version, LocalDate effectiveDate, PolicyDocumentStatus status,
                                     long fileSizeBytes, Instant extractionCompletedAt,
                                     String processingError, Instant createdAt, Instant updatedAt) {
    public static PolicyDocumentResponse from(PolicyDocument document) {
        return new PolicyDocumentResponse(document.getId(), document.getDocumentName(), document.getDocumentType(),
                document.getVersion(), document.getEffectiveDate(), document.getStatus(),
                document.getFileSizeBytes(), document.getExtractionCompletedAt(), document.getProcessingError(),
                document.getCreatedAt(), document.getUpdatedAt());
    }
}
