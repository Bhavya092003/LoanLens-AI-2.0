package com.lending.platform.application;

import com.lending.platform.common.persistence.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;

@Entity
@Table(name = "application_documents")
public class ApplicationDocument extends TimestampedEntity {
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private LoanApplication application;

    @NotBlank @Size(max = 64)
    @Column(name = "document_type", nullable = false, length = 64)
    private String documentType;

    @Size(max = 48)
    @Column(name = "storage_provider", length = 48)
    private String storageProvider;

    @Size(max = 512)
    @Column(name = "storage_key", length = 512)
    private String storageKey;

    @NotBlank @Size(max = 255)
    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @NotBlank @Size(max = 160)
    @Column(name = "content_type", nullable = false, length = 160)
    private String contentType;

    @NotNull @PositiveOrZero
    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Size(max = 64)
    @Column(name = "content_sha256", length = 64)
    private String contentSha256;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "document_status", nullable = false, length = 24)
    private DocumentStatus documentStatus;

    @NotNull
    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    protected ApplicationDocument() {}

    public ApplicationDocument(LoanApplication application, String documentType, String storageProvider,
                               String storageKey, String originalFilename, String contentType,
                               Long fileSizeBytes, String contentSha256, DocumentStatus documentStatus, Instant uploadedAt) {
        this.application = application;
        this.documentType = documentType;
        this.storageProvider = storageProvider;
        this.storageKey = storageKey;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.fileSizeBytes = fileSizeBytes;
        this.contentSha256 = contentSha256;
        this.documentStatus = documentStatus;
        this.uploadedAt = uploadedAt;
    }

    public LoanApplication getApplication() { return application; }
    public String getDocumentType() { return documentType; }
    public String getStorageProvider() { return storageProvider; }
    public String getStorageKey() { return storageKey; }
    public String getOriginalFilename() { return originalFilename; }
    public String getContentType() { return contentType; }
    public Long getFileSizeBytes() { return fileSizeBytes; }
    public String getContentSha256() { return contentSha256; }
    public DocumentStatus getDocumentStatus() { return documentStatus; }
    public Instant getUploadedAt() { return uploadedAt; }
}
