package com.lending.platform.audit;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
    java.util.Optional<AuditEvent> findTopByApplication_IdAndEventTypeOrderByCreatedAtDesc(UUID applicationId, String eventType);
}
