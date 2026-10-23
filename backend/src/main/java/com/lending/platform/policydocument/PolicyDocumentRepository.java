package com.lending.platform.policydocument;

import java.util.UUID;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;

public interface PolicyDocumentRepository extends JpaRepository<PolicyDocument, UUID> {
    boolean existsByDocumentNameIgnoreCaseAndVersion(String documentName, String version);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select document from PolicyDocument document where document.id = :id")
    Optional<PolicyDocument> findByIdForUpdate(@Param("id") UUID id);

    @Query("select document from PolicyDocument document " +
            "where lower(document.documentType) = lower(:documentType) " +
            "and (:version is null or document.version = :version) " +
            "and (:status is null or document.status = :status)")
    Page<PolicyDocument> findFiltered(@Param("documentType") String documentType,
                                      @Param("version") String version,
                                      @Param("status") PolicyDocumentStatus status,
                                      Pageable pageable);

    @Query("select document from PolicyDocument document " +
            "where (:version is null or document.version = :version) " +
            "and (:status is null or document.status = :status)")
    Page<PolicyDocument> findFilteredWithoutType(@Param("version") String version,
                                                 @Param("status") PolicyDocumentStatus status,
                                                 Pageable pageable);
}
