package com.lending.platform.policydocument;

import com.lending.platform.common.persistence.UuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "policy_chunks")
public class PolicyChunk extends UuidEntity {
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_document_id", nullable = false)
    private PolicyDocument policyDocument;

    @Min(0)
    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Size(max = 240)
    @Column(name = "section_title", length = 240)
    private String sectionTitle;

    @NotBlank
    @Column(name = "chunk_text", nullable = false, columnDefinition = "text")
    private String chunkText;

    @Min(1)
    @Column(name = "page_number")
    private Integer pageNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PolicyChunk() {}

    public PolicyChunk(PolicyDocument policyDocument, int chunkIndex, String sectionTitle, String chunkText,
                       Integer pageNumber, Map<String, Object> metadata) {
        this.policyDocument = policyDocument;
        this.chunkIndex = chunkIndex;
        this.sectionTitle = sectionTitle;
        this.chunkText = chunkText;
        this.pageNumber = pageNumber;
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public PolicyDocument getPolicyDocument() { return policyDocument; }
    public int getChunkIndex() { return chunkIndex; }
    public String getSectionTitle() { return sectionTitle; }
    public String getChunkText() { return chunkText; }
    public Integer getPageNumber() { return pageNumber; }
    public Map<String, Object> getMetadata() { return metadata; }
    public Instant getCreatedAt() { return createdAt; }
}
