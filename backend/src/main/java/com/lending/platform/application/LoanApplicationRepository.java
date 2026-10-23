package com.lending.platform.application;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface LoanApplicationRepository extends JpaRepository<LoanApplication, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select application from LoanApplication application where application.id=:id")
    java.util.Optional<LoanApplication> findByIdForUpdate(@Param("id") UUID id);

    List<LoanApplication> findTop50ByApplicationStatusInOrderByUpdatedAtDesc(
            List<ApplicationStatus> applicationStatuses);
}
