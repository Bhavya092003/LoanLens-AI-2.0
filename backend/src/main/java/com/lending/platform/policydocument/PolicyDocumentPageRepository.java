package com.lending.platform.policydocument;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyDocumentPageRepository extends JpaRepository<PolicyDocumentPage, UUID> {
    java.util.List<PolicyDocumentPage> findByPolicyDocument_IdOrderByPageNumber(UUID documentId);
    Page<PolicyDocumentPage> findByPolicyDocument_Id(UUID documentId, Pageable pageable);
    void deleteAllByPolicyDocument_Id(UUID documentId);
}
