package com.lending.platform.application;

import com.lending.platform.common.persistence.UuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "application_status_history")
public class ApplicationStatusHistory extends UuidEntity {
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private LoanApplication application;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 40)
    private ApplicationStatus previousStatus;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 40)
    private ApplicationStatus newStatus;

    @Column(name = "changed_by")
    private UUID changedBy;

    @Size(max = 1000)
    @Column(name = "reason", length = 1000)
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ApplicationStatusHistory() {}

    public ApplicationStatusHistory(LoanApplication application, ApplicationStatus previousStatus,
                                    ApplicationStatus newStatus, UUID changedBy, String reason) {
        this.application = application;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.changedBy = changedBy;
        this.reason = reason;
    }

    public LoanApplication getApplication() { return application; }
    public ApplicationStatus getPreviousStatus() { return previousStatus; }
    public ApplicationStatus getNewStatus() { return newStatus; }
    public UUID getChangedBy() { return changedBy; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
