package com.lending.platform.application;

import java.time.Instant;
import java.util.UUID;

public record ApplicationDocumentResponse(UUID documentId, UUID applicationId, String documentType,
                                          String storageProvider, String storageKey, String originalFilename,
                                          String contentType, long fileSizeBytes, DocumentStatus documentStatus,
                                          Instant uploadedAt, Instant updatedAt) {
    public static ApplicationDocumentResponse from(ApplicationDocument document) {
        return new ApplicationDocumentResponse(document.getId(), document.getApplication().getId(),
                document.getDocumentType(), document.getStorageProvider(), null,
                document.getOriginalFilename(), document.getContentType(), document.getFileSizeBytes(),
                document.getDocumentStatus(), document.getUploadedAt(), document.getUpdatedAt());
    }
}
