package com.lending.platform.policydocument;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyChunkRepository extends JpaRepository<PolicyChunk, UUID> {
    void deleteAllByPolicyDocument_Id(UUID documentId);
}
