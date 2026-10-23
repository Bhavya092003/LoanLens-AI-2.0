package com.lending.platform.application;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentCheckRepository extends JpaRepository<AgentCheck, UUID> {
    Page<AgentCheck> findByApplication_IdOrderByCreatedAtDesc(UUID applicationId, Pageable pageable);
}
