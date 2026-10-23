package com.lending.platform.policydocument;

import com.lending.platform.common.persistence.UuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "policy_document_pages")
public class PolicyDocumentPage extends UuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_document_id", nullable = false)
    private PolicyDocument policyDocument;

    @Column(name = "page_number", nullable = false)
    private int pageNumber;

    @Column(name = "extracted_text", nullable = false, columnDefinition = "text")
    private String extractedText;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PolicyDocumentPage() {}

    public PolicyDocumentPage(PolicyDocument policyDocument, int pageNumber, String extractedText) {
        this.policyDocument = policyDocument;
        this.pageNumber = pageNumber;
        this.extractedText = extractedText == null ? "" : extractedText;
    }

    public UUID getPolicyDocumentId() { return policyDocument.getId(); }
    public int getPageNumber() { return pageNumber; }
    public String getExtractedText() { return extractedText; }
    public Instant getCreatedAt() { return createdAt; }
}
