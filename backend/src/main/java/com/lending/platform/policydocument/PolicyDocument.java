package com.lending.platform.policydocument;

import com.lending.platform.common.persistence.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.Instant;

@Entity
@Table(name = "policy_documents")
public class PolicyDocument extends TimestampedEntity {
    @NotBlank @Size(max = 240)
    @Column(name = "document_name", nullable = false, length = 240)
    private String documentName;

    @NotBlank @Size(max = 64)
    @Column(name = "document_type", nullable = false, length = 64)
    private String documentType;

    @NotBlank @Size(max = 64)
    @Column(name = "version", nullable = false, length = 64)
    private String version;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private PolicyDocumentStatus status;

    @Size(max = 48)
    @Column(name = "storage_provider", length = 48)
    private String storageProvider;

    @Size(max = 512)
    @Column(name = "storage_key", length = 512)
    private String storageKey;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Size(max = 64)
    @Column(name = "content_sha256", length = 64)
    private String contentSha256;

    @Size(max = 500)
    @Column(name = "processing_error", length = 500)
    private String processingError;

    @Column(name = "extraction_completed_at")
    private Instant extractionCompletedAt;

    protected PolicyDocument() {}

    public PolicyDocument(String documentName, String documentType, String version, LocalDate effectiveDate,
                          PolicyDocumentStatus status, String storageProvider, String storageKey) {
        this.documentName = documentName;
        this.documentType = documentType;
        this.version = version;
        this.effectiveDate = effectiveDate;
        this.status = status;
        this.storageProvider = storageProvider;
        this.storageKey = storageKey;
    }

    public PolicyDocument(String documentName, String documentType, String version, LocalDate effectiveDate,
                          PolicyDocumentStatus status, String storageProvider, String storageKey,
                          long fileSizeBytes, String contentSha256) {
        this(documentName, documentType, version, effectiveDate, status, storageProvider, storageKey);
        this.fileSizeBytes = fileSizeBytes;
        this.contentSha256 = contentSha256;
    }

    public String getDocumentName() { return documentName; }
    public String getDocumentType() { return documentType; }
    public String getVersion() { return version; }
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public PolicyDocumentStatus getStatus() { return status; }
    public String getStorageProvider() { return storageProvider; }
    public String getStorageKey() { return storageKey; }
    public long getFileSizeBytes() { return fileSizeBytes; }
    public String getContentSha256() { return contentSha256; }
    public String getProcessingError() { return processingError; }
    public Instant getExtractionCompletedAt() { return extractionCompletedAt; }

    public void markProcessing() {
        this.status = PolicyDocumentStatus.PROCESSING;
        this.processingError = null;
    }

    public void completeExtraction(PolicyDocumentStatus completedStatus) {
        this.status = completedStatus;
        this.processingError = null;
        this.extractionCompletedAt = Instant.now();
    }

    public void markFailed(String safeReason) {
        this.status = PolicyDocumentStatus.FAILED;
        this.processingError = safeReason;
        this.extractionCompletedAt = null;
    }
}
