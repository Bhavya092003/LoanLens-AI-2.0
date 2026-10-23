package com.lending.platform.policydocument;

import java.time.Instant;
import java.util.UUID;

public record PolicyDocumentPageResponse(UUID documentId, int pageNumber, String text, Instant createdAt) {
    public static PolicyDocumentPageResponse from(PolicyDocumentPage page) {
        return new PolicyDocumentPageResponse(page.getPolicyDocumentId(), page.getPageNumber(),
                page.getExtractedText(), page.getCreatedAt());
    }
}
