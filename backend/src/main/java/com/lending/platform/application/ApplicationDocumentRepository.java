package com.lending.platform.application;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationDocumentRepository extends JpaRepository<ApplicationDocument, UUID> {
    Page<ApplicationDocument> findByApplication_IdOrderByUploadedAtDesc(UUID applicationId, Pageable pageable);
    java.util.Optional<ApplicationDocument> findFirstByApplication_IdAndDocumentTypeAndContentSha256(UUID applicationId, String documentType, String contentSha256);
    boolean existsByApplication_IdAndDocumentTypeIgnoreCaseAndDocumentStatus(UUID applicationId, String documentType, DocumentStatus documentStatus);
}
